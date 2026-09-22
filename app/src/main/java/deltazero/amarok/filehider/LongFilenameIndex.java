package deltazero.amarok.filehider;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the original names of entries whose obfuscated name will not fit in a directory entry.
 * <p>
 * Obfuscation renames a file to the Base64 of its name, which is a third longer than what it
 * started as. A name of about 186 bytes or more therefore encodes past the filesystem's limit,
 * the rename fails, and the file stays in plain sight. Base64 cannot be made to fit, so for those
 * names the file takes a short id instead and the name it had is recorded in an index beside it.
 * <p>
 * The index is written before the file is renamed. A run cut short can leave an entry with no
 * file, which costs nothing, but never a file whose name cannot be read back.
 * <p>
 * Contains no Android dependency, so it is covered by plain JVM unit tests.
 */
public class LongFilenameIndex {

    /** Longest single path component, in bytes, on the filesystems Android uses. */
    public static final int MAX_FILENAME_BYTES = 255;

    /**
     * The index's own name. It carries the no-process mark, so the walker reads it as already
     * processed and leaves it as it is through a hide.
     */
    public static final String INDEX_FILENAME = ".amk_names!amk";

    /** Base64 characters of the name's digest kept as its id. */
    private static final int ID_LENGTH = 16;

    private static final String SEPARATOR = "\t";

    private final Map<Path, Map<String, String>> cache = new HashMap<>();
    private final Set<Path> incomplete = new HashSet<>();

    /**
     * @param filename A candidate filename.
     * @return Whether the filesystem can hold a name this long.
     */
    public static boolean fits(String filename) {
        return filename.getBytes(UTF_8).length <= MAX_FILENAME_BYTES;
    }

    /**
     * A short id standing in for a name that is too long to obfuscate in place. The same name
     * always gets the same id, and two names in one directory differ, so the id identifies the
     * entry on the way back.
     *
     * @param originalName The name the entry had.
     * @return Its id.
     */
    public static String idFor(String originalName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(originalName.getBytes(UTF_8));
            return encode(digest).substring(0, ID_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }

    /**
     * Record what an entry was called, before it is renamed to its id.
     *
     * @param directory The directory holding the entry.
     * @param id        The id the entry is taking.
     * @param originalName The name the entry had.
     * @throws IOException If the index could not be written, in which case the caller must leave
     *                     the entry alone rather than rename it to a name it cannot undo.
     */
    public void record(Path directory, String id, String originalName) throws IOException {

        String line = id + SEPARATOR + encode(originalName.getBytes(UTF_8)) + "\n";

        Files.write(directory.resolve(INDEX_FILENAME), line.getBytes(UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        cache.remove(directory);
    }

    /**
     * Look up what an entry was called.
     *
     * @param directory The directory holding the entry.
     * @param id        The id the entry carries.
     * @return The name it had, or null if the index does not have it. A miss keeps the index in
     *         place, so a later run can still use whatever else is in it.
     */
    public String lookup(Path directory, String id) {

        String originalName = entries(directory).get(id);

        if (originalName == null)
            markIncomplete(directory);

        return originalName;
    }

    /**
     * Note that an entry in this directory could not be restored, so its index is still needed.
     *
     * @param directory The directory to keep the index of.
     */
    public void markIncomplete(Path directory) {
        incomplete.add(directory);
    }

    /**
     * Remove a directory's index once everything it named has been restored. Leaving it behind
     * would name files that are no longer hidden.
     *
     * @param directory The directory to clear up.
     * @throws IOException If the index is there but could not be removed.
     */
    public void discardIfComplete(Path directory) throws IOException {

        cache.remove(directory);

        if (incomplete.remove(directory))
            return;

        Files.deleteIfExists(directory.resolve(INDEX_FILENAME));
    }

    /**
     * The directory's entries, read once and then kept.
     * <p>
     * A line that cannot be read is skipped rather than failing the whole directory, so one bad
     * line does not strand every other name in the index.
     */
    private Map<String, String> entries(Path directory) {

        Map<String, String> cached = cache.get(directory);
        if (cached != null)
            return cached;

        Map<String, String> entries = new HashMap<>();
        Path index = directory.resolve(INDEX_FILENAME);

        try {
            if (Files.exists(index)) {
                for (String line : Files.readAllLines(index, UTF_8)) {
                    int separator = line.indexOf(SEPARATOR);
                    if (separator <= 0)
                        continue;

                    try {
                        entries.put(line.substring(0, separator),
                                new String(decode(line.substring(separator + 1)), UTF_8));
                    } catch (IllegalArgumentException e) {
                        // A line we cannot decode names nothing we can restore.
                    }
                }
            }
        } catch (IOException e) {
            // An unreadable index reads as an empty one: every lookup misses and nothing is
            // renamed, which leaves the files hidden rather than half restored.
            markIncomplete(directory);
        }

        cache.put(directory, entries);
        return entries;
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decode(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }
}
