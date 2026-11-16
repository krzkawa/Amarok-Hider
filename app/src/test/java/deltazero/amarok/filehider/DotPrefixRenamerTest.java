package deltazero.amarok.filehider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public class DotPrefixRenamerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path dir;
    private Path hidden;

    @Before
    public void setUp() throws IOException {
        dir = folder.newFolder("Private").toPath();
        hidden = folder.getRoot().toPath().resolve(".Private");
        Files.write(dir.resolve("photo.jpg"), "p".getBytes(UTF_8));
    }

    @Test
    public void hideAndUnhideRoundTrip() throws IOException {
        assertEquals(DotPrefixRenamer.Result.RENAMED, DotPrefixRenamer.hide(dir));
        assertFalse(Files.exists(dir));
        assertTrue(Files.exists(hidden.resolve("photo.jpg")));

        assertEquals(DotPrefixRenamer.Result.RENAMED, DotPrefixRenamer.unhide(dir));
        assertFalse(Files.exists(hidden));
        assertTrue(Files.exists(dir.resolve("photo.jpg")));
    }

    @Test
    public void hidingAgainPicksUpAFolderRecreatedWhileHidden() throws IOException {
        DotPrefixRenamer.hide(dir);
        Files.createDirectories(dir);
        Files.write(dir.resolve("new.jpg"), "n".getBytes(UTF_8));

        assertEquals(DotPrefixRenamer.Result.MERGED, DotPrefixRenamer.hide(dir));

        assertFalse(Files.exists(dir));
        assertTrue(Files.exists(hidden.resolve("photo.jpg")));
        assertTrue(Files.exists(hidden.resolve("new.jpg")));
    }

    @Test
    public void unhidingMergesIntoAFolderRecreatedWhileHidden() throws IOException {
        DotPrefixRenamer.hide(dir);
        Files.createDirectories(dir);
        Files.write(dir.resolve("new.jpg"), "n".getBytes(UTF_8));

        assertEquals(DotPrefixRenamer.Result.MERGED, DotPrefixRenamer.unhide(dir));

        assertFalse(Files.exists(hidden));
        assertTrue(Files.exists(dir.resolve("photo.jpg")));
        assertTrue(Files.exists(dir.resolve("new.jpg")));
    }

    @Test
    public void keepsTheFolderModifiedTime() throws IOException {
        FileTime old = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(dir, old);

        DotPrefixRenamer.hide(dir);

        assertEquals(old, Files.getLastModifiedTime(hidden));
    }

    @Test
    public void skipsWhatIsNotThereOrAlreadyDotted() throws IOException {
        assertEquals(DotPrefixRenamer.Result.SKIPPED, DotPrefixRenamer.unhide(dir));
        assertEquals(DotPrefixRenamer.Result.SKIPPED,
                DotPrefixRenamer.hide(folder.newFolder(".already").toPath()));
    }

    @Test
    public void aFileInTheWayBlocks() throws IOException {
        Files.write(hidden, "x".getBytes(UTF_8));

        assertEquals(DotPrefixRenamer.Result.BLOCKED, DotPrefixRenamer.hide(dir));
        assertTrue(Files.exists(dir.resolve("photo.jpg")));
    }
}
