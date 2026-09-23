package deltazero.amarok.filehider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public class FolderMergerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.write(path, content.getBytes(UTF_8));
    }

    private String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), UTF_8);
    }

    @Test
    public void movesEverythingAndRemovesTheEmptiedFolder() throws IOException {
        Path root = folder.getRoot().toPath();
        Path from = root.resolve("new");
        Path into = root.resolve("old");
        write(from.resolve("a.jpg"), "a");
        write(from.resolve("sub/b.jpg"), "b");
        write(into.resolve("c.jpg"), "c");

        assertTrue(FolderMerger.merge(from, into, entry -> true));

        assertFalse(Files.exists(from));
        assertEquals("a", read(into.resolve("a.jpg")));
        assertEquals("b", read(into.resolve("sub/b.jpg")));
        assertEquals("c", read(into.resolve("c.jpg")));
    }

    @Test
    public void mergesNestedFoldersOfTheSameName() throws IOException {
        Path root = folder.getRoot().toPath();
        Path from = root.resolve("new");
        Path into = root.resolve("old");
        write(from.resolve("2024/a.jpg"), "a");
        write(into.resolve("2024/b.jpg"), "b");

        assertTrue(FolderMerger.merge(from, into, entry -> true));

        assertEquals("a", read(into.resolve("2024/a.jpg")));
        assertEquals("b", read(into.resolve("2024/b.jpg")));
    }

    @Test
    public void neverOverwritesAFileWhoseNameIsTaken() throws IOException {
        Path root = folder.getRoot().toPath();
        Path from = root.resolve("new");
        Path into = root.resolve("old");
        write(from.resolve("same.jpg"), "new");
        write(from.resolve("other.jpg"), "other");
        write(into.resolve("same.jpg"), "old");

        assertFalse(FolderMerger.merge(from, into, entry -> true));

        assertEquals("old", read(into.resolve("same.jpg")));
        assertEquals("new", read(from.resolve("same.jpg")));
        assertEquals("other", read(into.resolve("other.jpg")));
    }

    @Test
    public void leavesEntriesTheFilterRefuses() throws IOException {
        Path root = folder.getRoot().toPath();
        Path from = root.resolve("new");
        Path into = root.resolve("old");
        write(from.resolve("keep.idx"), "index");
        write(from.resolve("move.jpg"), "m");
        Files.createDirectories(into);

        assertFalse(FolderMerger.merge(from, into,
                entry -> !entry.getFileName().toString().endsWith(".idx")));

        assertTrue(Files.exists(from.resolve("keep.idx")));
        assertTrue(Files.exists(into.resolve("move.jpg")));
    }

    @Test
    public void keepsTheModifiedTimeOfTheFolderMergedInto() throws IOException {
        Path root = folder.getRoot().toPath();
        Path from = root.resolve("new");
        Path into = root.resolve("old");
        write(from.resolve("a.jpg"), "a");
        Files.createDirectories(into);
        FileTime old = FileTime.fromMillis(1_000_000_000_000L);
        Files.setLastModifiedTime(into, old);

        FolderMerger.merge(from, into, entry -> true);

        assertEquals(old, Files.getLastModifiedTime(into));
    }
}
