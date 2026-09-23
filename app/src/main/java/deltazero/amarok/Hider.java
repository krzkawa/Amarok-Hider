package deltazero.amarok;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.MutableLiveData;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.Set;

import deltazero.amarok.apphider.AppHidePlan;

import deltazero.amarok.ui.settings.SwitchAppHiderActivity;
import deltazero.amarok.utils.AppStateUtil;
import deltazero.amarok.utils.SecurityUtil;


public final class Hider {

    private static final String TAG = "Hider";
    private static final HandlerThread hiderThread = new HandlerThread("HIDER_THREAD");
    private static final Handler threadHandler;

    public static boolean initialized = false;
    public static MutableLiveData<State> state;

    public enum State {
        HIDDEN,
        VISIBLE,
        PROCESSING
    }

    static {
        hiderThread.start();
        threadHandler = new Handler(hiderThread.getLooper());
    }

    /**
     * This method should be invoked in {@link AmarokApplication#onCreate()}, after {@link PrefMgr#init(Context)}.
     * Do not invoke this method in static part or before {@link PrefMgr#init(Context)}.
     */
    public static void init() {
        assert PrefMgr.initialized;
        state = new MutableLiveData<>(PrefMgr.getIsHidden() ? State.HIDDEN : State.VISIBLE);
        state.observeForever(state -> {
            if (state != State.PROCESSING)
                PrefMgr.setIsHidden(state == State.HIDDEN);
        });
        initialized = true;
    }

    /**
     * NOTE: Calling this method on a background thread
     * does not guarantee that the latest value set will be received.
     */
    public static State getState() {
        return state.getValue();
    }

    public static void hide(Context context) {
        PrefMgr.getAppHider(context).tryToActivate((appHiderClass, succeed, msg) -> {
            if (succeed) {
                processHide(context);
                return;
            }
            if (context instanceof Activity)
                showNoHiderDialog(context, msg);
            else
                showNoHiderToast(context, msg);
        });

        // Avoid password or disguise right after hide
        if (PrefMgr.getDisableSecurityWhenUnhidden()) {
            SecurityUtil.unlock();
            SecurityUtil.dismissDisguise();
        }
    }

    private static void processHide(Context context) {

        threadHandler.post(() -> {

            Log.i(TAG, "Process 'hide' start.");
            state.postValue(State.PROCESSING);

            try {
                hideApps(context);
                PrefMgr.getFileHider(context).hide(PrefMgr.getHideFilePath());
            } catch (InterruptedException e) {
                Log.w(TAG, "Process 'hide' interrupted.");
                return;
            }

            Log.i(TAG, "Process 'hide' finish.");
            state.postValue(State.HIDDEN);

            if (!PrefMgr.getDisableToasts())
                Toast.makeText(context, R.string.hidden_toast, Toast.LENGTH_SHORT).show();

            QuickHideService.stopService(context);

        });
    }

    public static void unhide(Context context) {
        PrefMgr.getAppHider(context).tryToActivate((appHiderClass, succeed, msg) -> {
            if (succeed) {
                processUnhide(context);
                return;
            }
            if (context instanceof Activity)
                showNoHiderDialog(context, msg);
            else
                showNoHiderToast(context, msg);
        });
    }

    private static void processUnhide(Context context) {

        threadHandler.post(() -> {

            Log.i(TAG, "Process 'unhide' start.");
            state.postValue(State.PROCESSING);

            try {
                unhideApps(context);
                PrefMgr.getFileHider(context).unhide(PrefMgr.getHideFilePath());
            } catch (InterruptedException e) {
                Log.w(TAG, "Process 'unhide' interrupted.");
                return;
            }

            Log.i(TAG, "Process 'unhide' finish.");
            state.postValue(State.VISIBLE);

            if (!PrefMgr.getDisableToasts())
                Toast.makeText(context, R.string.unhidden_toast, Toast.LENGTH_SHORT).show();

            // Important: The startService() method of QuickHideService must be invoked on the main thread.
            // If it's called from a background thread, the service might not get the most recent value from Hider.getState() in time.
            // As a result, if the state changes into VISIBLE from HIDDEN just before, the service won't start.
            new Handler(Looper.getMainLooper()).post(
                    () -> QuickHideService.startService(context));
        });
    }

    /**
     * Hide the apps. A failure here is reported and does not stop the files from being hidden.
     */
    private static void hideApps(Context context) {
        var appHider = PrefMgr.getAppHider(context);
        Set<String> apps = PrefMgr.getHideApps();
        Set<String> iconOnly = AppHidePlan.iconOnly(apps, PrefMgr.getIconOnlyApps(),
                appHider.supportsComponentHiding());
        Set<String> fully = AppHidePlan.fully(apps, iconOnly);

        // Determine if we should only disable apps (skip hide step) when XHide is enabled
        boolean disableOnly = PrefMgr.isXHideEnabled() && PrefMgr.getDisableOnlyWithXHide();

        // Only while everything is visible does an app's state belong to the user. Once hidden,
        // it is ours, so a second hide in a row must not overwrite what was recorded.
        if (!PrefMgr.getIsHidden())
            PrefMgr.setWereDisabledApps(AppStateUtil.findDisabled(context, fully));

        Set<String> components = PrefMgr.getIconHiddenComponents();
        components.addAll(AppStateUtil.findLauncherComponents(context, iconOnly));
        PrefMgr.setIconHiddenComponents(components);

        try {
            appHider.setComponentsEnabled(components, false);
            appHider.hide(fully, disableOnly);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to hide apps.", e);
            Toast.makeText(context, context.getString(R.string.hide_apps_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Unhide the apps. A failure here is reported and does not stop the files from being unhidden.
     */
    private static void unhideApps(Context context) {
        var appHider = PrefMgr.getAppHider(context);
        Set<String> components = PrefMgr.getIconHiddenComponents();
        Set<String> fully = AppHidePlan.fully(PrefMgr.getHideApps(), AppHidePlan.packagesOf(components));
        Set<String> leaveDisabled = AppHidePlan.leaveDisabled(fully, PrefMgr.getKeepDisabledApps(),
                PrefMgr.getWereDisabledApps());

        try {
            appHider.unhide(fully, leaveDisabled);
            if (!components.isEmpty())
                appHider.setComponentsEnabled(components, true);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to unhide apps.", e);
            Toast.makeText(context, context.getString(R.string.unhide_apps_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
            // Keep the records, so the next attempt puts the same things back.
            return;
        }

        PrefMgr.setIconHiddenComponents(new HashSet<>());
        PrefMgr.setWereDisabledApps(new HashSet<>());
    }

    public static void forceUnhide(Context context) {
        if (state.getValue() == State.PROCESSING)
            hiderThread.interrupt();
        PrefMgr.setIsHidden(true);
        unhide(context);
    }

    public static void showNoHiderDialog(Context context, int message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.apphider_not_ava_title)
                .setMessage(message)
                .setPositiveButton(R.string.switch_app_hider, (dialog, which)
                        -> context.startActivity(new Intent(context, SwitchAppHiderActivity.class)))
                .setNegativeButton(context.getString(R.string.ok), null)
                .show();
    }

    private static void showNoHiderToast(Context context, int message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

}
