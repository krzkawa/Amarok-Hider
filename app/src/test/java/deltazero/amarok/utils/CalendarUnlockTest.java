package deltazero.amarok.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public class CalendarUnlockTest {

    private static final LocalDate SECRET = LocalDate.of(2027, 3, 14);

    @Test
    public void withoutASecretDateTheYearOpens() {
        assertTrue(CalendarUnlock.opens(null, null));
        assertFalse(CalendarUnlock.opens(null, SECRET));
    }

    @Test
    public void withASecretDateOnlyThatDateOpens() {
        assertTrue(CalendarUnlock.opens(SECRET, LocalDate.of(2027, 3, 14)));
        assertFalse(CalendarUnlock.opens(SECRET, LocalDate.of(2026, 3, 14)));
        assertFalse(CalendarUnlock.opens(SECRET, null));
    }

    @Test
    public void parsesWhatItStores() {
        assertEquals(SECRET, CalendarUnlock.parseSecretDate(SECRET.toString()));
    }

    @Test
    public void ignoresAMissingOrMalformedDate() {
        assertNull(CalendarUnlock.parseSecretDate(null));
        assertNull(CalendarUnlock.parseSecretDate(""));
        assertNull(CalendarUnlock.parseSecretDate("14/03/2027"));
    }

    @Test
    public void holdTime() {
        assertEquals(0, CalendarUnlock.holdMillis(0));
        assertEquals(0, CalendarUnlock.holdMillis(-3));
        assertEquals(5000, CalendarUnlock.holdMillis(5));
    }
}
