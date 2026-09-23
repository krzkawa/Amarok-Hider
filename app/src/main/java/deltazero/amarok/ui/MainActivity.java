package deltazero.amarok.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.hjq.permissions.XXPermissions;

import java.util.ArrayList;
import java.util.Comparator;

import deltazero.amarok.AmarokActivity;
import deltazero.amarok.Hider;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.apphider.NoneAppHider;
import deltazero.amarok.filehider.NoneFileHider;
import deltazero.amarok.ui.settings.SettingsActivity;
import deltazero.amarok.ui.settings.SwitchAppHiderActivity;
import deltazero.amarok.ui.settings.SwitchFileHiderActivity;
import deltazero.amarok.utils.PermissionUtil;
import deltazero.amarok.utils.UpdateUtil;
import nl.dionsegijn.konfetti.xml.KonfettiView;

public class MainActivity extends AmarokActivity {

    public final static String TAG = "Main";
    private ImageView ivStatusImg;
    private TextView tvStatusInfo, tvStatus, tvMoto;
    private MaterialButton btChangeStatus, btSetHideFiles, btSetHideApps, btOpenHiddenApps;
    private CircularProgressIndicator piProcessStatus;
    private KonfettiView konfettiView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Setup activity
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Binding views
        ivStatusImg = findViewById(R.id.main_iv_status);
        tvStatus = findViewById(R.id.main_tv_status);
        tvStatusInfo = findViewById(R.id.main_tv_statusinfo);
        tvMoto = findViewById(R.id.main_tv_moto);
        btChangeStatus = findViewById(R.id.main_bt_change_status);
        btSetHideApps = findViewById(R.id.main_bt_set_hide_apps);
        btSetHideFiles = findViewById(R.id.main_bt_set_hide_files);
        btOpenHiddenApps = findViewById(R.id.main_bt_open_hidden_apps);
        piProcessStatus = findViewById(R.id.main_pi_process_status);
        konfettiView = findViewById(R.id.main_konfetti_view);

        // Init UI
        refreshUi(Hider.getState());

        // Setup observer
        Hider.state.observe(this, this::refreshUi);

        // Show welcome dialog
        if (PrefMgr.getShowWelcome()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.welcome_title)
                    .setMessage(R.string.welcome_msg)
                    .setPositiveButton(R.string.ok, (dialog, which)
                            -> PermissionUtil.requestStoragePermission(this))
                    .setNegativeButton(R.string.view_github_repo, (dialog, which) -> {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/deltazefiro/Amarok-Hider")));
                        PermissionUtil.requestStoragePermission(this);
                    })
                    .setOnCancelListener(dialog -> PermissionUtil.requestStoragePermission(this))
                    .show();
            PrefMgr.setShowWelcome(false);
        } else {
            PermissionUtil.requestStoragePermission(this);
        }

        // Check Hiders availability
        PrefMgr.getAppHider(this).tryToActivate((appHiderClass, succeed, msg) -> {
            if (succeed) return;
            Hider.showNoHiderDialog(this, msg);
        });

        PrefMgr.getFileHider(this).tryToActive((fileHiderClass, succeed, msg) -> {
            if (succeed) return;
            PrefMgr.setFileHiderMode(NoneFileHider.class);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.filehider_not_ava_title)
                    .setMessage(msg)
                    .setPositiveButton(R.string.switch_file_hider, (dialog, which)
                            -> startActivity(new Intent(this, SwitchFileHiderActivity.class)))
                    .setNegativeButton(getString(R.string.ok), null)
                    .show();
        });

        // Check for updates
        if (PrefMgr.getEnableAutoUpdate()) {
            UpdateUtil.checkAndNotify(this, true);
        }
    }

    public void changeStatus(View view) {
        if (Hider.getState() == Hider.State.HIDDEN) Hider.unhide(this);
        else Hider.hide(this);
    }

    public void setHideApps(View view) {

        if (Hider.getState() == Hider.State.HIDDEN) {
            Toast.makeText(this, R.string.setting_not_ava_when_hidden, Toast.LENGTH_SHORT).show();
            return;
        }

        if (PrefMgr.getAppHider(this) instanceof NoneAppHider) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.apphider_not_activated_title)
                    .setMessage(R.string.apphider_not_activated_msg)
                    .setPositiveButton(R.string.switch_app_hider, (dialog, which)
                            -> startActivity(new Intent(this, SwitchAppHiderActivity.class)))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
            return;
        }

        startActivity(new Intent(this, SetHideAppActivity.class));

    }

    /**
     * Lists the apps on the hidden list so they can be opened straight from here, instead of
     * hunted for in the app drawer after unhiding.
     */
    public void openHiddenApps(View view) {

        if (Hider.getState() != Hider.State.VISIBLE) {
            Toast.makeText(this, R.string.setting_not_ava_when_hidden, Toast.LENGTH_SHORT).show();
            return;
        }

        var pm = getPackageManager();
        var apps = new ArrayList<ApplicationInfo>();
        for (String pkgName : PrefMgr.getHideApps()) {
            try {
                apps.add(pm.getApplicationInfo(pkgName, 0));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Uninstalled since it was added.
            }
        }

        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.no_hidden_apps, Toast.LENGTH_SHORT).show();
            return;
        }

        apps.sort(Comparator.comparing(a -> pm.getApplicationLabel(a).toString()));

        var adapter = new ArrayAdapter<ApplicationInfo>(this, R.layout.item_launch_app, apps) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View item = convertView != null ? convertView
                        : getLayoutInflater().inflate(R.layout.item_launch_app, parent, false);
                ApplicationInfo app = getItem(position);
                assert app != null;
                ((ImageView) item.findViewById(R.id.launch_app_iv_icon)).setImageDrawable(pm.getApplicationIcon(app));
                ((TextView) item.findViewById(R.id.launch_app_tv_label)).setText(pm.getApplicationLabel(app));
                return item;
            }
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.open_hidden_apps)
                .setAdapter(adapter, (dialog, which) -> {
                    Intent launchIntent = pm.getLaunchIntentForPackage(apps.get(which).packageName);
                    if (launchIntent == null) {
                        Toast.makeText(this, R.string.app_cannot_be_opened, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startActivity(launchIntent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void showMoreSettings(View view) {

        startActivity(new Intent(this, SettingsActivity.class));

    }

    public void setHideFile(View view) {

        if (!XXPermissions.isGranted(this, com.hjq.permissions.Permission.MANAGE_EXTERNAL_STORAGE)) {
            Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_LONG).show();
            return;
        }

        if (Hider.getState() == Hider.State.HIDDEN) {
            Toast.makeText(this, R.string.setting_not_ava_when_hidden, Toast.LENGTH_SHORT).show();
            return;
        }

        startActivity(new Intent(this, SetHideFilesActivity.class));
    }

    public void refreshUi(Hider.State state) {
        tvMoto.setText(R.string.moto);
        switch (state) {
            case HIDDEN -> {
                // Not Processing
                piProcessStatus.hide();
                btChangeStatus.setEnabled(true);
                // Hidden
                ivStatusImg.setImageResource(R.drawable.img_status_hidden);
                ivStatusImg.setImageTintList(getColorStateList(com.google.android.material.R.color.material_on_background_emphasis_high_type));
                btChangeStatus.setText(R.string.unhide);
                btChangeStatus.setIconResource(R.drawable.ic_wolf);
                btSetHideFiles.setEnabled(false);
                btSetHideApps.setEnabled(false);
                btOpenHiddenApps.setEnabled(false);
                tvStatus.setText(getText(R.string.hidden_status));
                tvStatusInfo.setText(getText(R.string.hidden_moto));
            }
            case VISIBLE -> {
                // Not Processing
                piProcessStatus.hide();
                btChangeStatus.setEnabled(true);
                // Visible
                ivStatusImg.setImageResource(R.drawable.img_status_visible);
                ivStatusImg.setImageTintList(null);
                btChangeStatus.setText(R.string.hide);
                btChangeStatus.setIconResource(R.drawable.ic_paw);
                btSetHideFiles.setEnabled(true);
                btSetHideApps.setEnabled(true);
                btOpenHiddenApps.setEnabled(true);
                tvStatus.setText(getText(R.string.visible_status));
                tvStatusInfo.setText(getText(R.string.visible_moto));
            }
            case PROCESSING -> {
                // Processing
                piProcessStatus.show();
                btChangeStatus.setEnabled(false);
            }
        }
    }

    @Override
    protected void onResume() {
        refreshUi(Hider.getState());
        super.onResume();
    }
}




