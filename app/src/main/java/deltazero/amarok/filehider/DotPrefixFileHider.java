package deltazero.amarok.filehider;

import android.content.Context;
import android.util.Log;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import deltazero.amarok.utils.MediaStoreHelper;

/**
 * Hides each folder by putting a dot in front of its name, which galleries and file managers treat
 * as hidden. Only the folder itself is renamed, so it is immediate however many files it holds.
 */
public class DotPrefixFileHider extends BaseFileHider {

    private static final String TAG = "DotPrefixFileHider";

    public DotPrefixFileHider(Context context) {
        super(context);
    }

    @Override
    protected void process(Set<String> targetDirs, ProcessMethod method) throws InterruptedException {
        var scanDirs = new HashSet<String>();

        for (var dir : targetDirs) {

            if (Thread.interrupted())
                throw new InterruptedException();

            Log.i(TAG, String.format("Processing: %s", dir));

            try {
                var path = Paths.get(dir);
                var result = method == ProcessMethod.HIDE
                        ? DotPrefixRenamer.hide(path)
                        : DotPrefixRenamer.unhide(path);
                Log.i(TAG, String.format("%s: %s", dir, result));
                scanDirs.add(path.toString());
                scanDirs.add(DotPrefixRenamer.hiddenPath(path).toString());
            } catch (Exception e) {
                Log.w(TAG, String.format("Error while processing %s: ", dir), e);
            }
        }

        MediaStoreHelper.scan(context, scanDirs);
    }

    @Override
    public void tryToActive(ActivationCallbackListener activationCallbackListener) {
        activationCallbackListener.onActivateCallback(this.getClass(), true, 0);
    }

    @Override
    public String getName() {
        return "DotPrefix";
    }
}
