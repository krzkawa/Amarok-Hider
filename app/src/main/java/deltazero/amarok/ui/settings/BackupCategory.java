package deltazero.amarok.ui.settings;

import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

import deltazero.amarok.Hider;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.utils.SettingsBackup;

public class BackupCategory extends BaseCategory {

    private static final String TAG = "BackupCategory";
    private static final String BACKUP_FILENAME = "amarok-settings.json";

    private final ActivityResultLauncher<String> exportLauncher;
    private final ActivityResultLauncher<String[]> importLauncher;

    /**
     * @param fragment The settings screen, which the file pickers report back to. It must not be
     *                 started yet, so build this category while its preferences are created.
     */
    public BackupCategory(@NonNull FragmentActivity activity, @NonNull PreferenceScreen screen,
                          @NonNull Fragment fragment) {
        super(activity, screen);
        setTitle(R.string.backup);

        exportLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.CreateDocument(SettingsBackup.MIME_TYPE),
                uri -> { if (uri != null) exportTo(uri); });
        importLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) importFrom(uri); });

        var exportPref = new Preference(activity);
        exportPref.setTitle(R.string.export_settings);
        exportPref.setIcon(R.drawable.ic_restore);
        exportPref.setSummary(R.string.export_settings_description);
        exportPref.setOnPreferenceClickListener(preference -> {
            exportLauncher.launch(BACKUP_FILENAME);
            return true;
        });
        addPreference(exportPref);

        var importPref = new Preference(activity);
        importPref.setTitle(R.string.import_settings);
        importPref.setIcon(R.drawable.settings_backup_restore_black_24dp);
        importPref.setSummary(R.string.import_settings_description);
        importPref.setOnPreferenceClickListener(preference -> {
            // Folders and apps swapped in while hidden would be unhidden by the wrong list.
            if (Hider.getState() != Hider.State.VISIBLE) {
                Toast.makeText(activity, R.string.setting_not_ava_when_hidden, Toast.LENGTH_SHORT).show();
                return true;
            }
            importLauncher.launch(new String[]{SettingsBackup.MIME_TYPE, "text/plain", "application/octet-stream"});
            return true;
        });
        addPreference(importPref);
    }

    private void exportTo(Uri uri) {
        try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("Cannot open " + uri);
            out.write(SettingsBackup.export(PrefMgr.getPrefs().getAll()).getBytes());
            Toast.makeText(activity, R.string.export_settings_done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w(TAG, "Export failed", e);
            Toast.makeText(activity, activity.getString(R.string.export_settings_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void importFrom(Uri uri) {
        Map<String, Object> prefs;
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open " + uri);
            var buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            prefs = SettingsBackup.parse(buffer.toString());
        } catch (Exception e) {
            Log.w(TAG, "Import failed", e);
            Toast.makeText(activity, R.string.import_settings_invalid, Toast.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.import_settings)
                .setMessage(R.string.import_settings_confirm)
                .setPositiveButton(R.string.confirm, (dialog, which) -> apply(prefs))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> prefs) {
        SharedPreferences.Editor editor = PrefMgr.getPrefs().edit();

        // Replace, rather than add to, whatever is set now.
        for (String key : PrefMgr.getPrefs().getAll().keySet())
            if (!SettingsBackup.EXCLUDED_KEYS.contains(key))
                editor.remove(key);

        for (var e : prefs.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean b) editor.putBoolean(e.getKey(), b);
            else if (v instanceof Integer i) editor.putInt(e.getKey(), i);
            else if (v instanceof Long l) editor.putLong(e.getKey(), l);
            else if (v instanceof Float f) editor.putFloat(e.getKey(), f);
            else if (v instanceof String s) editor.putString(e.getKey(), s);
            else if (v instanceof Set<?> set) editor.putStringSet(e.getKey(), (Set<String>) set);
        }

        editor.commit();
        Toast.makeText(activity, R.string.import_settings_done, Toast.LENGTH_LONG).show();
        activity.recreate();
    }
}
