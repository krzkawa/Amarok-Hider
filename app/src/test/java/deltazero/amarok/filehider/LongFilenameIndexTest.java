package deltazero.amarok.filehider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class LongFilenameIndexTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private LongFilenameIndex index;
    private Path directory;

    @Before
    public void setUp() throws IOException {
        index = new LongFilenameIndex();
        directory = folder.newFolder("target").toPath();
    }

    private Path indexFile() {
        return directory.resolve(LongFilenameIndex.INDEX_FILENAME);
    }

    /** The name the hider would rename a file to, built exactly as it builds it. */
    private static String obfuscatedName(String filename, String mark) {
        return "." + Base64.getUrlEncoder().withoutPadding().encodeToString(filename.getBytes(UTF_8))
                + mark;
    }

    private static String repeat(char c, int count) {
        return String.valueOf(c).repeat(count);
    }

    @Test
    public void fits_measuresTheNameInBytesRatherThanCharacters() {
        assertTrue(LongFilenameIndex.fits(repeat('a', LongFilenameIndex.MAX_FILENAME_BYTES)));
        assertFalse(LongFilenameIndex.fits(repeat('a', LongFilenameIndex.MAX_FILENAME_BYTES + 1)));

        // Each of these is three bytes of UTF-8, so far fewer of them fit.
        assertTrue(LongFilenameIndex.fits(repeat('中', LongFilenameIndex.MAX_FILENAME_BYTES / 3)));
        assertFalse(LongFilenameIndex.fits(repeat('中', LongFilenameIndex.MAX_FILENAME_BYTES / 3 + 1)));
    }

    @Test
    public void fits_marksTheNameLengthWhereBase64StopsFitting() {
        // Base64 is a third longer than what it encodes, so the limit falls well short of 255.
        assertTrue(LongFilenameIndex.fits(
                obfuscatedName(repeat('a', 186), ObfuscateFileHider.FILENAME_FULL_PROCESS_MARK)));
        assertFalse(LongFilenameIndex.fits(
                obfuscatedName(repeat('a', 187), ObfuscateFileHider.FILENAME_FULL_PROCESS_MARK)));
    }

    @Test
    public void fits_acceptsEveryNameTheIndexHandsBack() {
        // Whatever the original name, the id form always fits, which is the point of it.
        String id = LongFilenameIndex.idFor(repeat('a', 4096));
        assertTrue(LongFilenameIndex.fits("." + id + ObfuscateFileHider.FILENAME_FULL_PROCESS_LONG_MARK));
    }

    @Test
    public void idFor_isStableAndDistinguishesNames() {
        assertEquals(LongFilenameIndex.idFor("holiday.jpg"), LongFilenameIndex.idFor("holiday.jpg"));
        assertNotEquals(LongFilenameIndex.idFor("holiday.jpg"), LongFilenameIndex.idFor("holiday.png"));
    }

    @Test
    public void idFor_neverProducesASeparatorOrPathCharacter() {
        String id = LongFilenameIndex.idFor("a/b\tc\nd");
        assertFalse(id.contains("/"));
        assertFalse(id.contains("\t"));
        assertFalse(id.contains("\n"));
    }

    @Test
    public void recordThenLookup_bringsTheNameBack() throws IOException {
        String name = repeat('a', 200) + ".jpg";
        String id = LongFilenameIndex.idFor(name);

        index.record(directory, id, name);
        assertEquals(name, new LongFilenameIndex().lookup(directory, id));
    }

    @Test
    public void record_writesTheIndexBeforeTheFileIsRenamed() throws IOException {
        index.record(directory, LongFilenameIndex.idFor("a"), "a");
        assertTrue(Files.exists(indexFile()));
    }

    @Test
    public void record_keepsEveryNameInADirectory() throws IOException {
        String first = repeat('a', 200);
        String second = repeat('b', 200);

        index.record(directory, LongFilenameIndex.idFor(first), first);
        index.record(directory, LongFilenameIndex.idFor(second), second);

        var reader = new LongFilenameIndex();
        assertEquals(first, reader.lookup(directory, LongFilenameIndex.idFor(first)));
        assertEquals(second, reader.lookup(directory, LongFilenameIndex.idFor(second)));
    }

    @Test
    public void recordThenLookup_survivesNamesWithAwkwardCharacters() throws IOException {
        String name = "中文 \t nameé😀" + repeat('z', 200);
        String id = LongFilenameIndex.idFor(name);

        index.record(directory, id, name);
        assertEquals(name, new LongFilenameIndex().lookup(directory, id));
    }

    @Test
    public void lookup_returnsNullWhenThereIsNoIndex() {
        assertNull(index.lookup(directory, LongFilenameIndex.idFor("missing")));
    }

    @Test
    public void discardIfComplete_removesTheIndexOnceEveryNameIsBack() throws IOException {
        String name = repeat('a', 200);
        index.record(directory, LongFilenameIndex.idFor(name), name);

        var reader = new LongFilenameIndex();
        assertEquals(name, reader.lookup(directory, LongFilenameIndex.idFor(name)));
        reader.discardIfComplete(directory);

        assertFalse(Files.exists(indexFile()));
    }

    @Test
    public void discardIfComplete_keepsTheIndexWhenANameCouldNotBeRestored() throws IOException {
        String name = repeat('a', 200);
        index.record(directory, LongFilenameIndex.idFor(name), name);

        var reader = new LongFilenameIndex();
        assertNull(reader.lookup(directory, "anIdThatIsNotThere"));
        reader.discardIfComplete(directory);

        // The index still names a file sitting there hidden, so losing it would strand the name.
        assertTrue(Files.exists(indexFile()));
        assertEquals(name, new LongFilenameIndex().lookup(directory, LongFilenameIndex.idFor(name)));
    }

    @Test
    public void discardIfComplete_keepsTheIndexWhenARenameFailed() throws IOException {
        String name = repeat('a', 200);
        index.record(directory, LongFilenameIndex.idFor(name), name);

        var reader = new LongFilenameIndex();
        assertEquals(name, reader.lookup(directory, LongFilenameIndex.idFor(name)));
        reader.markIncomplete(directory);
        reader.discardIfComplete(directory);

        assertTrue(Files.exists(indexFile()));
    }

    @Test
    public void discardIfComplete_isHarmlessWhereThereIsNoIndex() throws IOException {
        index.discardIfComplete(directory);
        assertFalse(Files.exists(indexFile()));
    }

    @Test
    public void aDamagedLineDoesNotStrandTheRestOfTheIndex() throws IOException {
        String name = repeat('a', 200);
        index.record(directory, LongFilenameIndex.idFor(name), name);
        Files.write(indexFile(), "not an entry\nalsoBad\t!!!not base64!!!\n".getBytes(UTF_8),
                java.nio.file.StandardOpenOption.APPEND);

        assertEquals(name, new LongFilenameIndex().lookup(directory, LongFilenameIndex.idFor(name)));
    }
}
