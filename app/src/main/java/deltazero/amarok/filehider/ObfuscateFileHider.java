package deltazero.amarok.filehider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static deltazero.amarok.filehider.BaseFileHider.ProcessMethod.HIDE;
import static deltazero.amarok.filehider.BaseFileHider.ProcessMethod.UNHIDE;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.utils.FileHiderUtil;
import deltazero.amarok.utils.MediaStoreHelper;

public class ObfuscateFileHider extends BaseFileHider {
    private final static String TAG = "FileHider";

    private final static int MAX_PROCESS_WHOLE_FILE_SIZE_KB = 10 * 1024; // In KB.
    private final static int MAX_PROCESS_ENHANCED_WHOLE_FILE_SIZE_KB = 30 * 1024;
    private final static int BASE64_TAG = Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING;

    public final static String FILENAME_NO_PROCESS_MARK = "!amk";
    public final static String FILENAME_FULL_PROCESS_MARK = "!amk1";
    public final static String FILENAME_HEADER_PROCESS_MARK = "!amk2";

    // Counterparts for a name too long to obfuscate in place. The file carries a short id and
    // the name it had is kept in the directory's LongFilenameIndex.
    public final static String FILENAME_NO_PROCESS_LONG_MARK = "!amk3";
    public final static String FILENAME_FULL_PROCESS_LONG_MARK = "!amk4";
    public final static String FILENAME_HEADER_PROCESS_LONG_MARK = "!amk5";

    public boolean processHeader;
    public boolean processTextFile;
    public boolean processTextFileEnhanced;

    public ObfuscateFileHider(Context context) {
        super(context);
        processHeader = PrefMgr.getEnableObfuscateFileHeader();
        processTextFile = PrefMgr.getEnableObfuscateTextFile();
        processTextFileEnhanced = PrefMgr.getEnableObfuscateTextFileEnhanced();
    }

    @Override
    protected void process(Set<String> targetDirs, ProcessMethod method) throws InterruptedException {
        var longFilenameIndex = new LongFilenameIndex();
        for (var dir : targetDirs) {
            try {
                processTree(Paths.get(dir), method, longFilenameIndex);
            } catch (InterruptedException e) {
                throw new InterruptedException();
            } catch (Exception e) {
                Log.w(TAG, String.format("Failed to process %s: ", dir), e);
            }
        }
        MediaStoreHelper.scan(context, targetDirs);
    }

    @Override
    public void tryToActive(ActivationCallbackListener activationCallbackListener) {
        activationCallbackListener.onActivateCallback(this.getClass(), true, 0);
    }

    @Override
    public String getName() {
        return "Obfuscate";
    }

    private void processTree(Path targetDir, ProcessMethod method, LongFilenameIndex longFilenameIndex)
            throws InterruptedException {

        Log.i(TAG, "Start to process file tree: " + targetDir);

        // Renaming what is inside a folder counts as a change to the folder, which would bring
        // every hidden folder to the top of a list sorted by date. Put the dates back afterwards.
        Map<Path, FileTime> folderTimes = new HashMap<>();

        try {

            Files.walkFileTree(targetDir, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    folderTimes.put(dir, attrs.lastModifiedTime());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {

                    // Handle interruption
                    if (Thread.currentThread().isInterrupted()) {
                        Log.w(TAG, "File process interrupted.");
                        return FileVisitResult.TERMINATE;
                    }

                    // Skip .nomedia
                    if (path.getFileName().toString().equals(".nomedia"))
                        return FileVisitResult.CONTINUE;

                    // Skip the index of long names: it has to keep its own name to be found.
                    if (path.getFileName().toString().equals(LongFilenameIndex.INDEX_FILENAME))
                        return FileVisitResult.CONTINUE;

                    // Check whether the whole file should be processed before renaming (processFilename).
                    boolean shouldProcessHeader = checkShouldProcessHeader(path, method);
                    boolean shouldProcessWhole = checkShouldProcessWhole(path, method);

                    // Choose filename ending mark
                    String endingMark = FILENAME_NO_PROCESS_MARK;
                    if (shouldProcessHeader)
                        endingMark = FILENAME_HEADER_PROCESS_MARK;
                    if (shouldProcessWhole)
                        endingMark = FILENAME_FULL_PROCESS_MARK;

                    // Process filename
                    Path newPath = processFilename(path, method, endingMark, longFilenameIndex);

                    // Process file content
                    if (newPath != null) {
                        if (shouldProcessWhole) { // Check shouldProcessWhole first.
                            processWholeFile(newPath);
                        } else if (shouldProcessHeader) { // Then check shouldProcessHeader.
                            processFileHeader(newPath);
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                    // Clear the directory's own index before it is renamed along with the directory.
                    if (method == UNHIDE) {
                        try {
                            longFilenameIndex.discardIfComplete(dir);
                        } catch (IOException ioException) {
                            Log.w(TAG, "Failed to remove the long name index of " + dir, ioException);
                        }
                    }

                    // Before the rename below, which leaves a folder's own time alone.
                    restoreModifiedTime(dir, folderTimes.remove(dir));

                    if (dir != targetDir)
                        processFilename(dir, method, FILENAME_NO_PROCESS_MARK, longFilenameIndex);

                    return FileVisitResult.CONTINUE;
                }

            });

        } catch (IOException e) {
            Log.w(TAG, String.format("While processing '%s': %s", targetDir.getFileName(), e));
        }

        // Clear interrupted flag & throw InterruptedException
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    /**
     * Process the Filename and check if the process succeeds.
     *
     * @param path            The path to be processed.
     * @param method          Process method.
     * @param extraEndingMark (only effective when `HIDE`) Extra mark to be append to the end of the filename.
     * @param longFilenameIndex Holds the names that are too long to obfuscate in place.
     * @return If the process succeeds, return the new path. Otherwise, return null.
     */
    @Nullable
    private Path processFilename(Path path, ProcessMethod method, String extraEndingMark,
                                 LongFilenameIndex longFilenameIndex) {
        String filename = path.getFileName().toString();
        String newFilename;
        Path newPath;

        boolean hasEncoded = FileHiderUtil.checkIsMarkInFilename(filename);
        boolean isLongName = FileHiderUtil.checkIsLongMarkInFilename(filename);

        if (method == HIDE) {
            if (hasEncoded) {
                Log.d(TAG, "Found encoded name: " + filename + ", skip...");
                return null;
            }

            newFilename = "." + Base64.encodeToString(filename.getBytes(UTF_8), BASE64_TAG) + extraEndingMark;

            if (!LongFilenameIndex.fits(newFilename)) {
                // Base64 is a third longer than what it encodes, so a long name cannot be
                // obfuscated in place. It takes an id instead, recorded before the rename.
                newFilename = encodeLongFilename(path, filename, extraEndingMark, longFilenameIndex);
                if (newFilename == null)
                    return null;
            }

            Log.d(TAG, "Encode: " + path + " -> " + newFilename);

        } else {
            if (!hasEncoded) {
                Log.w(TAG, "Found not coded name: " + filename + ", skip...");
                return null;
            }

            if (isLongName) {
                String id = FileHiderUtil.stripFilenameExtras(filename);
                newFilename = longFilenameIndex.lookup(path.getParent(), id);

                if (newFilename == null) {
                    // Without its name the file stays as it is, hidden but whole, so a later run
                    // can still restore it if the index comes back.
                    Log.w(TAG, "No recorded name for: " + filename);
                    return null;
                }

            } else {
                try {
                    newFilename = new String(
                            Base64.decode(FileHiderUtil.stripFilenameExtras(filename), BASE64_TAG), UTF_8
                    );
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Unable to decode: " + filename);
                    return null;
                }
            }

            Log.d(TAG, "Decode: " + path + " -> " + newFilename);
        }

        // Try to rename.

        newPath = Paths.get(path.getParent().toString(), newFilename);

        boolean is_succeeded = path.toFile().renameTo(newPath.toFile());

        if (!is_succeeded && path.toFile().isDirectory() && newPath.toFile().isDirectory()) {
            // The name is taken by a folder, e.g. an app recreated "Screenshots" while the original
            // was hidden. Merge the two rather than leave one of them behind.
            return mergeFolders(path, newPath);
        }

        if (!is_succeeded) {
            Log.w(TAG, "Error when renaming file: " + path + " -> " + newPath);

            // The index still names a file that is sitting there hidden, so it has to stay.
            if (method == UNHIDE && isLongName)
                longFilenameIndex.markIncomplete(path.getParent());

            return null;
        } else {
            return newPath;
        }
    }

    /**
     * Move the contents of a folder into the folder that holds the name it should take.
     *
     * @return The folder merged into, or null if nothing could be merged.
     */
    @Nullable
    private Path mergeFolders(Path from, Path into) {
        Log.i(TAG, "Merging " + from + " into " + into);
        try {
            // A name kept in an index has to stay beside that index, so those files stay put.
            boolean complete = FolderMerger.merge(from, into, entry -> {
                String name = entry.getFileName().toString();
                return !name.equals(LongFilenameIndex.INDEX_FILENAME)
                        && !FileHiderUtil.checkIsLongMarkInFilename(name);
            });
            if (!complete)
                Log.w(TAG, "Some entries were left in " + from + ", as their names are taken.");
            return into;
        } catch (IOException e) {
            Log.w(TAG, "Failed to merge " + from + " into " + into, e);
            return null;
        }
    }

    private static void restoreModifiedTime(Path path, @Nullable FileTime modified) {
        if (modified == null)
            return;
        try {
            Files.setLastModifiedTime(path, modified);
        } catch (IOException | SecurityException e) {
            Log.d(TAG, "Unable to restore the modified time of " + path, e);
        }
    }

    /**
     * Give a file whose obfuscated name would not fit a short id instead, recording what it was
     * called in the index beside it.
     *
     * @return The name to rename it to, or null if the name could not be recorded, in which case
     *         the file is left alone rather than given a name nothing can undo.
     */
    @Nullable
    private String encodeLongFilename(Path path, String filename, String extraEndingMark,
                                      LongFilenameIndex longFilenameIndex) {

        String id = LongFilenameIndex.idFor(filename);

        try {
            longFilenameIndex.record(path.getParent(), id, filename);
        } catch (IOException e) {
            Log.w(TAG, "Failed to record the name of " + path + ", leaving it visible: ", e);
            return null;
        }

        return "." + id + longMarkFor(extraEndingMark);
    }

    /**
     * @param extraEndingMark The mark the file would have carried.
     * @return Its counterpart for a name kept in the index.
     */
    private static String longMarkFor(String extraEndingMark) {
        if (FILENAME_FULL_PROCESS_MARK.equals(extraEndingMark))
            return FILENAME_FULL_PROCESS_LONG_MARK;
        if (FILENAME_HEADER_PROCESS_MARK.equals(extraEndingMark))
            return FILENAME_HEADER_PROCESS_LONG_MARK;
        return FILENAME_NO_PROCESS_LONG_MARK;
    }

    private void processFileHeader(Path path) {
        Log.d(TAG, "Processing file header: " + path);

        try {

            File file = path.toFile();

            // Preserve original lastModified time
            var lastModified = path.toFile().lastModified();

            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {

                byte[] bytes = new byte[8];
                int numBytesRead = randomAccessFile.read(bytes);
                int numBytesToReplace = Math.max(Math.min(numBytesRead, 8), 0);

                for (int i = 0; i < numBytesToReplace; i++) {
                    bytes[i] = (byte) ~bytes[i];
                }

                randomAccessFile.seek(0);
                randomAccessFile.write(bytes, 0, numBytesToReplace);

            } catch (IOException e) {
                Log.w(TAG, "processFileHeader failed: ", e);
            }

            // noinspection ResultOfMethodCallIgnored
            file.setLastModified(lastModified);

        } catch (SecurityException e) {
            Log.w(TAG, "processFileHeader failed: ", e);
        }
    }

    private void processWholeFile(Path path) {
        Log.d(TAG, "Processing whole file: " + path);

        byte[] buffer = new byte[1024];
        long numReadLoops = 0;
        int numBytesRead, numBytesToReplace;

        try {

            File file = path.toFile();

            // Preserve original lastModified time
            var lastModified = path.toFile().lastModified();

            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
                while ((numBytesRead = randomAccessFile.read(buffer)) != -1) {

                    numBytesToReplace = Math.max(Math.min(numBytesRead, buffer.length), 0);

                    for (int i = 0; i < numBytesToReplace; i++) {
                        buffer[i] = (byte) ~buffer[i];
                    }

                    randomAccessFile.seek(numReadLoops * buffer.length);
                    randomAccessFile.write(buffer, 0, numBytesToReplace);

                    numReadLoops++;
                }
            } catch (IOException e) {
                Log.w(TAG, "processWholeFile failed: ", e);
            }

            // noinspection ResultOfMethodCallIgnored
            file.setLastModified(lastModified);

        } catch (SecurityException e) {
            Log.w(TAG, "processWholeFile failed: ", e);
        }
    }

    private boolean checkShouldProcessWhole(Path path, ProcessMethod method) {

        if ((!processHeader) || (!processTextFile)) {
            return false;
        }

        String filename = path.getFileName().toString();
        if (method == HIDE) {

            if (processTextFileEnhanced) {
                return FileHiderUtil.checkIsTextFileEnhanced(path)
                        && FileHiderUtil.getFileSizeKB(path) <= MAX_PROCESS_WHOLE_FILE_SIZE_KB;
            } else { // Not enhanced
                return FileHiderUtil.checkIsTextFile(filename)
                        && FileHiderUtil.getFileSizeKB(path) <= MAX_PROCESS_ENHANCED_WHOLE_FILE_SIZE_KB;
            }

        } else { // UNHIDE
            return filename.endsWith(FILENAME_FULL_PROCESS_MARK)
                    || filename.endsWith(FILENAME_FULL_PROCESS_LONG_MARK);
        }
    }

    private boolean checkShouldProcessHeader(Path path, ProcessMethod method) {
        String filename = path.getFileName().toString();
        if (method == HIDE) return processHeader;
        else return filename.endsWith(FILENAME_HEADER_PROCESS_MARK)
                || filename.endsWith(FILENAME_HEADER_PROCESS_LONG_MARK);
    }
}
