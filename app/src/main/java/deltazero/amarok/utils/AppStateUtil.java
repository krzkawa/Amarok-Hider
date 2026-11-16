package deltazero.amarok.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Reads the state of other apps that hiding changes, so it can be put back afterwards.
 */
public final class AppStateUtil {

    private static final String TAG = "AppStateUtil";

    private AppStateUtil() {
    }

    /**
     * @return The apps among pkgNames that are currently disabled.
     */
    public static Set<String> findDisabled(Context context, Set<String> pkgNames) {
        var pm = context.getPackageManager();
        var result = new HashSet<String>();
        for (String p : pkgNames) {
            try {
                int state = pm.getApplicationEnabledSetting(p);
                if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
                    result.add(p);
            } catch (IllegalArgumentException e) {
                // Not installed, or already hidden from us.
                Log.d(TAG, "Unable to read the state of " + p);
            }
        }
        return result;
    }

    /**
     * @return The launcher activities of the apps among pkgNames that are currently enabled, as
     * "package/class".
     */
    public static Set<String> findLauncherComponents(Context context, Set<String> pkgNames) {
        var pm = context.getPackageManager();
        var result = new HashSet<String>();
        for (String p : pkgNames) {
            var intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(p);
            for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
                // For an activity-alias this is the alias, which is what the launcher shows.
                result.add(p + "/" + info.activityInfo.name);
            }
        }
        return result;
    }
}
