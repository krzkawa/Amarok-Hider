package deltazero.amarok.filehider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * The renaming behind {@link DotPrefixFileHider}. Free of Android dependencies, so it is covered
 * by the JVM unit tests.
 */
public final class DotPrefixRenamer {

    public enum Result {
        /** Renamed. */
        RENAMED,
        /** The other name was taken by a folder, and everything was merged into it. */
        MERGED,
        /** Merged, except for files whose names were taken, which stayed where they were. */
        PARTLY_MERGED,
        /** Nothing to do: the folder is not there, or is hidden by name already. */
        SKIPPED,
        /** A file stands where the folder should go. */
        BLOCKED
    }

    private DotPrefixRenamer() {
    }

    /**
     * @return Where the folder lives while hidden.
     */
    public static Path hiddenPath(Path dir) {
        return dir.resolveSibling("." + dir.getFileName().toString());
    }

    /**
     * Rename the folder to its hidden name. If a hidden copy is already there, which happens when
     * the folder was recreated while hidden, merge the new files into it.
     */
    public static Result hide(Path dir) throws IOException {
        if (dir.getFileName().toString().startsWith("."))
            return Result.SKIPPED;
        return move(dir, hiddenPath(dir));
    }

    /**
     * Rename the folder back. If the visible name was taken meanwhile, merge into that folder.
     */
    public static Result unhide(Path dir) throws IOException {
        if (dir.getFileName().toString().startsWith("."))
            return Result.SKIPPED;
        return move(hiddenPath(dir), dir);
    }

    private static Result move(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS))
            return Result.SKIPPED;

        if (Files.notExists(to, LinkOption.NOFOLLOW_LINKS)) {
            var modified = Files.getLastModifiedTime(from);
            Files.move(from, to);
            Files.setLastModifiedTime(to, modified);
            return Result.RENAMED;
        }

        if (!Files.isDirectory(to, LinkOption.NOFOLLOW_LINKS))
            return Result.BLOCKED;

        return FolderMerger.merge(from, to, entry -> true) ? Result.MERGED : Result.PARTLY_MERGED;
    }
}
