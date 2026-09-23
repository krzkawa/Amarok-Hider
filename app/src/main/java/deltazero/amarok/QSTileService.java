package deltazero.amarok;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import deltazero.amarok.ui.SecurityAuthForQSActivity;
import deltazero.amarok.utils.SecurityUtil;

public class QSTileService extends TileService {

    private static final String TAG = "TileService";
    public static boolean initialized = false;

    @Override
    public void onStartListening() {
        Log.i(TAG, "Tile update triggered.");

        Tile tile = getQsTile();
        boolean discreet = PrefMgr.getDiscreetTile();
        String label = getString(discreet ? R.string.discreet_tile_label : R.string.app_name);
        tile.setIcon(Icon.createWithResource(this, discreet ? R.drawable.brightness_empty_24dp_1f1f1f_fill0_wght400_grad0_opsz24 : R.drawable.ic_paw));

        switch (Hider.getState()) {
            case PROCESSING -> {
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.setLabel(discreet ? label : getString(R.string.processing));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    tile.setSubtitle(null);
            }
            case VISIBLE -> {
                tile.setLabel(label);
                tile.setState(determineTileState(false));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !discreet)
                    tile.setStateDescription(getString(R.string.visible_status));
            }
            case HIDDEN -> {
                tile.setLabel(label);
                tile.setState(determineTileState(true));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !discreet)
                    tile.setStateDescription(getString(R.string.hidden_status));
            }
            default -> throw new IllegalStateException("Unexpected value: " + Hider.getState());
        }

        // A neutral On / Off under the name, as system tiles have. It follows the tile's
        // colour rather than the hidden state, so it gives nothing away.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && tile.getState() != Tile.STATE_UNAVAILABLE)
            tile.setSubtitle(getString(tile.getState() == Tile.STATE_ACTIVE ? R.string.tile_on : R.string.tile_off));

        tile.updateTile();
    }

    /**
     * Ask the system to redraw the tile, e.g. after one of its settings changed.
     */
    public static void refresh(Context context) {
        try {
            TileService.requestListeningState(context, new ComponentName(context, QSTileService.class));
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "QuickSetting is unavailable when running in an Android work profile.");
        }
    }

    @Override
    public void onClick() {
        unlockAndRun(() -> {
            Log.i(TAG, "Toggled tile.");
            switch (Hider.getState()) {
                case VISIBLE -> Hider.hide(this);
                case HIDDEN -> {
                    if (SecurityUtil.isUnlockRequired()) startAuthThenUnhide();
                    else Hider.unhide(this);
                }
                default -> throw new IllegalStateException("Unexpected value: " + Hider.getState());
            }
        });
    }

    /**
     * The method should be invoked in {@link AmarokApplication#onCreate()}, after {@link Hider#state} is initialized.
     *
     * @param context Application context
     */
    public static void init(Context context) {
        assert Hider.initialized;
        Hider.state.observeForever(state -> refresh(context));
        initialized = true;
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private void startAuthThenUnhide() {
        var intent = new Intent(this, SecurityAuthForQSActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0,
                    intent, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private int determineTileState(boolean isHidden) {
        if (PrefMgr.getInvertTileColor())
            return isHidden ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        return isHidden ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE;
    }
}
