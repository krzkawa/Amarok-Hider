package deltazero.amarok.ui.settings;


import android.content.DialogInterface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.ui.CountdownConfirmDialog;
import deltazero.amarok.ui.SetPasswordFragment;
import deltazero.amarok.utils.CalculatorInput;
import deltazero.amarok.utils.CalendarUnlock;
import deltazero.amarok.utils.DisguiseType;
import deltazero.amarok.utils.HashUtil;
import deltazero.amarok.utils.LauncherIconController;
import deltazero.amarok.utils.SecurityUtil;
import rikka.material.preference.MaterialSwitchPreference;

public class PrivacyCategory extends BaseCategory {

    private MaterialSwitchPreference biometricPref;
    private Preference[] calendarPrefs;

    public PrivacyCategory(@NonNull FragmentActivity activity, PreferenceScreen screen) {
        super(activity, screen);
        setTitle(R.string.security);

        var appLockPref = new MaterialSwitchPreference(activity);
        appLockPref.setTitle(R.string.app_lock);
        appLockPref.setIcon(R.drawable.lock_black_24dp);
        appLockPref.setSummary(R.string.app_lock_description);
        appLockPref.setChecked(PrefMgr.getAmarokPassword() != null);
        appLockPref.setOnPreferenceClickListener(preference -> {
            if (appLockPref.isChecked()) {
                new SetPasswordFragment()
                        .setCallback(password -> {
                            PrefMgr.setAmarokPassword(password == null ? null : HashUtil.calculateHash(password));
                            SecurityUtil.unlock(); /* Avoid password right after enable the app lock */
                            appLockPref.setChecked(password != null);
                            biometricPref.setEnabled(PrefMgr.getAmarokPassword() != null);
                        })
                        .show(activity.getSupportFragmentManager(), null);
            } else {
                PrefMgr.setAmarokPassword(null);
                biometricPref.setEnabled(PrefMgr.getAmarokPassword() != null);
            }
            return false;
        });
        addPreference(appLockPref);

        biometricPref = new MaterialSwitchPreference(activity);
        biometricPref.setKey(PrefMgr.ENABLE_AMAROK_BIOMETRIC_AUTH);
        biometricPref.setIcon(R.drawable.fingerprint_24dp_1f1f1f_fill0_wght400_grad0_opsz24);
        biometricPref.setTitle(R.string.biometric_auth);
        biometricPref.setSummary(R.string.biometric_auth_description);
        biometricPref.setChecked(PrefMgr.getEnableAmarokBiometricAuth());
        biometricPref.setEnabled(PrefMgr.getAmarokPassword() != null);
        addPreference(biometricPref);

        var disguisePref = new MaterialSwitchPreference(activity);
        disguisePref.setKey(PrefMgr.ENABLE_DISGUISE);
        disguisePref.setIcon(R.drawable.calendar_month_24dp_1f1f1f_fill0_wght400_grad0_opsz24);
        disguisePref.setTitle(R.string.disguise);
        disguisePref.setSummary(R.string.disguise_description);
        disguisePref.setChecked(PrefMgr.getEnableDisguise());
        disguisePref.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enableDisguise = (boolean) newValue;
            PrefMgr.setDoShowQuitDisguiseInstuct(true);
            if (enableDisguise)
                SecurityUtil.lockAndDisguise();
            LauncherIconController.setIconState(activity, enableDisguise
                    ? PrefMgr.getDisguiseType().iconState
                    : LauncherIconController.IconState.VISIBLE);
            return true;
        });
        addPreference(disguisePref);

        var unlockEquationPref = new Preference(activity);
        unlockEquationPref.setKey(PrefMgr.CALCULATOR_UNLOCK_EQUATION);
        unlockEquationPref.setIcon(R.drawable.ic_lock);
        unlockEquationPref.setTitle(R.string.calculator_unlock_equation);
        unlockEquationPref.setSummary(PrefMgr.getCalculatorUnlockEquation());
        unlockEquationPref.setVisible(PrefMgr.getDisguiseType() == DisguiseType.CALCULATOR);
        unlockEquationPref.setOnPreferenceClickListener(preference -> {
            showUnlockEquationDialog(unlockEquationPref);
            return true;
        });

        var calendarHoldPref = new Preference(activity);
        calendarHoldPref.setKey(PrefMgr.CALENDAR_HOLD_SECONDS);
        calendarHoldPref.setIcon(R.drawable.timer_fill0_wght400_grad0_opsz24);
        calendarHoldPref.setTitle(R.string.calendar_hold_time);
        calendarHoldPref.setSummary(holdTimeLabel(PrefMgr.getCalendarHoldSeconds()));
        calendarHoldPref.setOnPreferenceClickListener(preference -> {
            showCalendarHoldDialog(calendarHoldPref);
            return true;
        });

        var calendarSecretDatePref = new Preference(activity);
        calendarSecretDatePref.setKey(PrefMgr.CALENDAR_SECRET_DATE);
        calendarSecretDatePref.setIcon(R.drawable.calendar_month_24dp_1f1f1f_fill0_wght400_grad0_opsz24);
        calendarSecretDatePref.setTitle(R.string.calendar_secret_date);
        calendarSecretDatePref.setSummary(secretDateLabel());
        calendarSecretDatePref.setOnPreferenceClickListener(preference -> {
            showSecretDateDialog(calendarSecretDatePref);
            return true;
        });

        calendarPrefs = new Preference[]{calendarHoldPref, calendarSecretDatePref};
        for (var pref : calendarPrefs)
            pref.setVisible(PrefMgr.getDisguiseType() == DisguiseType.CALENDAR);

        var disguiseTypePref = new Preference(activity);
        disguiseTypePref.setKey(PrefMgr.DISGUISE_TYPE);
        disguiseTypePref.setIcon(R.drawable.domino_mask_fill0_wght400_grad0_opsz24);
        disguiseTypePref.setTitle(R.string.disguise_type);
        disguiseTypePref.setSummary(PrefMgr.getDisguiseType().labelResId);
        disguiseTypePref.setOnPreferenceClickListener(preference -> {
            showDisguiseTypeDialog(disguiseTypePref, unlockEquationPref);
            return true;
        });
        addPreference(disguiseTypePref);
        addPreference(unlockEquationPref);
        addPreference(calendarHoldPref);
        addPreference(calendarSecretDatePref);

        var hideAmarokIconPref = new MaterialSwitchPreference(activity);
        hideAmarokIconPref.setKey(PrefMgr.HIDE_AMAROK_ICON);
        hideAmarokIconPref.setIcon(R.drawable.hide_source_black_24dp);
        hideAmarokIconPref.setTitle(R.string.hide_amarok_icon);
        hideAmarokIconPref.setSummary(R.string.hide_amarok_icon_description);
        hideAmarokIconPref.setChecked(PrefMgr.getHideAmarokIcon());
        hideAmarokIconPref.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean hideIcon = (boolean) newValue;
            if (hideIcon) {
                // Show countdown dialog before hiding icon
                new CountdownConfirmDialog.Builder(activity)
                        .setTitle(R.string.hide_amarok_icon_dialog_title)
                        .setMessage(R.string.hide_amarok_icon_dialog_message)
                        .setCountdownSeconds(10)
                        .setOnConfirmAction(() -> {
                            // When hiding icon, disable disguise and turn it off
                            disguisePref.setChecked(false);
                            disguisePref.setEnabled(false);
                            disguiseTypePref.setEnabled(false);
                            unlockEquationPref.setEnabled(false);
                            LauncherIconController.setIconState(activity, LauncherIconController.IconState.HIDDEN);
                            hideAmarokIconPref.setChecked(true);
                        })
                        .setOnCancelAction(() -> {
                            // User cancelled, revert the switch state
                            hideAmarokIconPref.setChecked(false);
                        })
                        .show();
                return false; // Don't change the preference yet
            } else {
                // When showing icon, re-enable disguise option
                disguisePref.setEnabled(true);
                disguiseTypePref.setEnabled(true);
                unlockEquationPref.setEnabled(true);
                LauncherIconController.setIconState(activity, LauncherIconController.IconState.VISIBLE);
                return true;
            }
        });
        // Set initial state: if icon is hidden, disable disguise option
        disguisePref.setEnabled(!PrefMgr.getHideAmarokIcon());
        disguiseTypePref.setEnabled(!PrefMgr.getHideAmarokIcon());
        unlockEquationPref.setEnabled(!PrefMgr.getHideAmarokIcon());
        addPreference(hideAmarokIconPref);

        var hideFromRecentsPref = new MaterialSwitchPreference(activity);
        hideFromRecentsPref.setKey(PrefMgr.HIDE_FROM_RECENTS);
        hideFromRecentsPref.setIcon(R.drawable.search_activity_24dp_1f1f1f_fill0_wght400_grad0_opsz24);
        hideFromRecentsPref.setTitle(R.string.hide_from_recents);
        hideFromRecentsPref.setSummary(R.string.hide_from_recents_description);
        hideFromRecentsPref.setChecked(PrefMgr.getHideFromRecents());
        hideFromRecentsPref.setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(activity, R.string.apply_on_restart, Toast.LENGTH_SHORT).show();
            return true;
        });
        addPreference(hideFromRecentsPref);

        var allowScreenshotPref = new MaterialSwitchPreference(activity);
        allowScreenshotPref.setKey(PrefMgr.BLOCK_SCREENSHOTS);
        allowScreenshotPref.setIcon(R.drawable.cancel_presentation_24dp_1f1f1f_fill0_wght400_grad0_opsz24);
        allowScreenshotPref.setTitle(R.string.block_screenshots);
        allowScreenshotPref.setSummary(R.string.block_screenshots_description);
        allowScreenshotPref.setChecked(PrefMgr.getBlockScreenshots());
        allowScreenshotPref.setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(activity, R.string.apply_on_restart, Toast.LENGTH_SHORT).show();
            return true;
        });
        addPreference(allowScreenshotPref);

        var disableSecurityWhenUnhiddenPref = new MaterialSwitchPreference(activity);
        disableSecurityWhenUnhiddenPref.setKey(PrefMgr.DISABLE_SECURITY_WHEN_UNHIDDEN);
        disableSecurityWhenUnhiddenPref.setIcon(R.drawable.encrypted_off_24dp);
        disableSecurityWhenUnhiddenPref.setTitle(R.string.disable_security_when_unhidden);
        disableSecurityWhenUnhiddenPref.setSummary(R.string.disable_security_when_unhidden_description);
        disableSecurityWhenUnhiddenPref.setChecked(PrefMgr.getDisableSecurityWhenUnhidden());
        addPreference(disableSecurityWhenUnhiddenPref);

        var disableToastsPref = new MaterialSwitchPreference(activity);
        disableToastsPref.setKey(PrefMgr.DISABLE_TOASTS);
        disableToastsPref.setIcon(R.drawable.speaker_notes_off_24dp);
        disableToastsPref.setTitle(R.string.disable_toasts);
        disableToastsPref.setSummary(R.string.disable_toasts_description);
        disableToastsPref.setChecked(PrefMgr.getDisableToasts());
        addPreference(disableToastsPref);
    }

    private void showDisguiseTypeDialog(Preference disguiseTypePref, Preference unlockEquationPref) {

        DisguiseType[] types = DisguiseType.values();
        String[] labels = new String[types.length];
        int checked = 0;

        for (int i = 0; i < types.length; i++) {
            labels[i] = activity.getString(types[i].labelResId);
            if (types[i] == PrefMgr.getDisguiseType())
                checked = i;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.disguise_type)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    DisguiseType selected = types[which];
                    PrefMgr.setDisguiseType(selected);
                    PrefMgr.setDoShowQuitDisguiseInstuct(true);

                    disguiseTypePref.setSummary(selected.labelResId);
                    unlockEquationPref.setVisible(selected == DisguiseType.CALCULATOR);
                    for (var pref : calendarPrefs)
                        pref.setVisible(selected == DisguiseType.CALENDAR);

                    // The launcher icon has to match whichever app Amarok is pretending to be.
                    if (PrefMgr.getEnableDisguise())
                        LauncherIconController.setIconState(activity, selected.iconState);

                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String holdTimeLabel(int seconds) {
        return seconds <= 0
                ? activity.getString(R.string.calendar_hold_time_default)
                : activity.getResources().getQuantityString(R.plurals.calendar_hold_time_seconds, seconds, seconds);
    }

    private String secretDateLabel() {
        LocalDate date = CalendarUnlock.parseSecretDate(PrefMgr.getCalendarSecretDate());
        return date == null
                ? activity.getString(R.string.calendar_secret_date_off)
                : activity.getString(R.string.calendar_secret_date_on,
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date));
    }

    private void showCalendarHoldDialog(Preference calendarHoldPref) {
        int[] options = CalendarUnlock.HOLD_SECONDS_OPTIONS;
        String[] labels = new String[options.length];
        int checked = 0;
        for (int i = 0; i < options.length; i++) {
            labels[i] = holdTimeLabel(options[i]);
            if (options[i] == PrefMgr.getCalendarHoldSeconds())
                checked = i;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.calendar_hold_time)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    PrefMgr.setCalendarHoldSeconds(options[which]);
                    calendarHoldPref.setSummary(labels[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSecretDateDialog(Preference calendarSecretDatePref) {
        if (PrefMgr.getCalendarSecretDate() == null) {
            pickSecretDate(calendarSecretDatePref);
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.calendar_secret_date)
                .setMessage(secretDateLabel())
                .setPositiveButton(R.string.calendar_secret_date_change, (dialog, which) -> pickSecretDate(calendarSecretDatePref))
                .setNegativeButton(R.string.calendar_secret_date_turn_off, (dialog, which) -> {
                    PrefMgr.setCalendarSecretDate(null);
                    // The year works again, so the tip is worth showing again.
                    PrefMgr.setDoShowQuitDisguiseInstuct(true);
                    calendarSecretDatePref.setSummary(secretDateLabel());
                })
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    private void pickSecretDate(Preference calendarSecretDatePref) {
        // The calendar only scrolls 100 months either way, so keep the date within reach.
        YearMonth now = YearMonth.now();
        long start = now.minusMonths(100).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long end = now.plusMonths(100).atEndOfMonth().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        var picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.calendar_secret_date)
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setStart(start)
                        .setEnd(end)
                        .setValidator(CompositeDateValidator.allOf(List.of(
                                DateValidatorPointForward.from(start),
                                DateValidatorPointBackward.before(end))))
                        .build())
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            LocalDate date = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate();
            PrefMgr.setCalendarSecretDate(date.toString());
            calendarSecretDatePref.setSummary(secretDateLabel());
        });
        picker.show(activity.getSupportFragmentManager(), null);
    }

    private void showUnlockEquationDialog(Preference unlockEquationPref) {

        var view = activity.getLayoutInflater().inflate(R.layout.dialog_equation_input, null);
        TextInputLayout inputLayout = view.findViewById(R.id.dialog_equation_input_til_input);
        TextInputEditText input = view.findViewById(R.id.dialog_equation_input_et_input);
        input.setText(PrefMgr.getCalculatorUnlockEquation());

        var dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.calculator_unlock_equation)
                .setMessage(R.string.calculator_unlock_equation_dialog_message)
                .setView(view)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .show();

        // Set the listener after showing, so an invalid equation leaves the dialog open.
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String equation = input.getText() == null ? "" : input.getText().toString();
            inputLayout.setError(null);

            if (!CalculatorInput.isTypeable(equation)) {
                inputLayout.setError(activity.getString(R.string.calculator_unlock_equation_invalid));
                return;
            }

            PrefMgr.setCalculatorUnlockEquation(equation);
            PrefMgr.setDoShowQuitDisguiseInstuct(true);
            unlockEquationPref.setSummary(equation);
            dialog.dismiss();
        });
    }
}
