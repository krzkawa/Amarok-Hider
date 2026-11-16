package deltazero.amarok.filehider;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Moves the contents of one folder into another folder of the same name, for when a folder
 * cannot simply be renamed because the name it should take is already in use, e.g. an app
 * recreated "Screenshots" while the original was hidden.
 * <p>
 * Nothing is overwritten: a file whose name is taken on the other side stays where it is, and so
 * does the folder holding it. Free of Android dependencies, so it is covered by the JVM unit tests.
 */
public final class FolderMerger {

    private FolderMerger() {
    }

    /**
     * @param from    The folder to empty.
     * @param into    The folder to move its contents into.
     * @param canMove Whether an entry of {@code from} may be moved. One that may not stays put.
     * @return Whether everything moved, in which case {@code from} is gone.
     */
    public static boolean merge(Path from, Path into, Predicate<Path> canMove) throws IOException {
        FileTime intoModified = Files.getLastModifiedTime(into);
        boolean complete = true;

        for (Path entry : list(from)) {
            if (!canMove.test(entry)) {
                complete = false;
                continue;
            }

            Path target = into.resolve(entry.getFileName().toString());

            if (Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.move(entry, target);
            } else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
                    && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                complete &= merge(entry, target, canMove);
            } else {
                // Same name, different file: keep both, as they are.
                complete = false;
            }
        }

        if (complete)
            Files.delete(from);

        // Moving entries in counts as a change to the folder, which would give it away.
        Files.setLastModifiedTime(into, intoModified);

        return complete;
    }

    private static List<Path> list(Path dir) throws IOException {
        var entries = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream)
                entries.add(p);
        }
        return entries;
    }
}
