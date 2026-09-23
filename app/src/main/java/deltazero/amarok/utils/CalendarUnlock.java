package deltazero.amarok.utils;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * How the calendar disguise is dismissed: by holding the year, or a secret date instead, for as
 * long as the user set. Free of Android dependencies, so it is covered by the JVM unit tests.
 */
public final class CalendarUnlock {

    /** The hold times offered, in seconds. 0 stands for the system's own long press. */
    public static final int[] HOLD_SECONDS_OPTIONS = {0, 1, 2, 3, 5, 10};

    private CalendarUnlock() {
    }

    /**
     * @return How long to hold, in milliseconds, or 0 to use the system's long press.
     */
    public static long holdMillis(int holdSeconds) {
        return holdSeconds <= 0 ? 0 : holdSeconds * 1000L;
    }

    /**
     * @param stored A date as stored in the preferences, possibly missing or malformed.
     * @return The date, or null if there is none to use.
     */
    @Nullable
    public static LocalDate parseSecretDate(@Nullable String stored) {
        if (stored == null || stored.isEmpty())
            return null;
        try {
            return LocalDate.parse(stored);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * @param secretDate The secret date, or null if none is set.
     * @param heldDate   The date held, or null if it was the year that was held.
     * @return Whether holding it dismisses the disguise. With a secret date set, holding the year
     * does nothing, so the usual trick no longer gives the disguise away.
     */
    public static boolean opens(@Nullable LocalDate secretDate, @Nullable LocalDate heldDate) {
        if (secretDate == null)
            return heldDate == null;
        return secretDate.equals(heldDate);
    }
}
