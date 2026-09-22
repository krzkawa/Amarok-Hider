package deltazero.amarok.utils;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import deltazero.amarok.R;
import deltazero.amarok.ui.CalculatorActivity;
import deltazero.amarok.ui.CalendarActivity;

/**
 * The app Amarok presents itself as while disguised.
 */
public enum DisguiseType {

    CALENDAR("calendar", R.string.disguise_type_calendar, R.string.app_name_calendar,
            CalendarActivity.class, LauncherIconController.IconState.DISGUISED_CALENDAR),

    CALCULATOR("calculator", R.string.disguise_type_calculator, R.string.app_name_calculator,
            CalculatorActivity.class, LauncherIconController.IconState.DISGUISED_CALCULATOR);

    /** Stored in the preferences, so it must stay stable across releases. */
    public final String key;

    @StringRes
    public final int labelResId;

    /** The name the launcher shows while this disguise is on. */
    @StringRes
    public final int appNameResId;

    public final Class<? extends Activity> activityClass;

    public final LauncherIconController.IconState iconState;

    DisguiseType(String key, @StringRes int labelResId, @StringRes int appNameResId,
                 Class<? extends Activity> activityClass,
                 LauncherIconController.IconState iconState) {
        this.key = key;
        this.labelResId = labelResId;
        this.appNameResId = appNameResId;
        this.activityClass = activityClass;
        this.iconState = iconState;
    }

    /**
     * @param key A stored key, possibly from an older or newer release.
     * @return The matching disguise, falling back to {@link #CALENDAR} for anything unknown.
     */
    @NonNull
    public static DisguiseType fromKey(@Nullable String key) {
        for (DisguiseType type : values()) {
            if (type.key.equals(key))
                return type;
        }
        return CALENDAR;
    }
}
