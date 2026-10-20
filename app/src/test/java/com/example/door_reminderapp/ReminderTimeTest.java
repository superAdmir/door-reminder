package com.example.door_reminderapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ReminderTimeTest {

    private static final TimeZone STOCKHOLM = TimeZone.getTimeZone("Europe/Stockholm");
    private static final TimeZone NEW_YORK = TimeZone.getTimeZone("America/New_York");
    private static final long HOUR = 60L * 60L * 1000L;

    private static long at(TimeZone zone, int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(zone);
        c.clear();
        c.set(year, month - 1, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void validation_rejectsOutOfRangeValues() {
        assertTrue(ReminderTime.isValid(0, 0));
        assertTrue(ReminderTime.isValid(23, 59));
        assertFalse(ReminderTime.isValid(24, 0));
        assertFalse(ReminderTime.isValid(-1, 0));
        assertFalse(ReminderTime.isValid(12, 60));
        assertFalse(ReminderTime.isValid(12, -1));
        assertThrows(IllegalArgumentException.class, () -> ReminderTime.of(24, 0));
    }

    @Test
    public void nextTrigger_laterToday_isToday() {
        long now = at(STOCKHOLM, 2026, 10, 13, 10, 0);
        long trigger = ReminderTime.of(21, 0).nextTriggerAfter(now, STOCKHOLM);
        assertEquals(at(STOCKHOLM, 2026, 10, 13, 21, 0), trigger);
    }

    @Test
    public void nextTrigger_alreadyPassedToday_isTomorrow() {
        // Version 1.0 scheduled a time in the past, which made Android fire it immediately.
        long now = at(STOCKHOLM, 2026, 10, 13, 22, 0);
        long trigger = ReminderTime.of(21, 0).nextTriggerAfter(now, STOCKHOLM);
        assertEquals(at(STOCKHOLM, 2026, 10, 14, 21, 0), trigger);
    }

    @Test
    public void nextTrigger_exactlyNow_isTomorrow() {
        long now = at(STOCKHOLM, 2026, 10, 13, 21, 0);
        long trigger = ReminderTime.of(21, 0).nextTriggerAfter(now, STOCKHOLM);
        assertEquals(at(STOCKHOLM, 2026, 10, 14, 21, 0), trigger);
    }

    @Test
    public void nextTrigger_ignoresSecondsOfNow() {
        long now = at(STOCKHOLM, 2026, 10, 13, 20, 59) + 59_999L;
        long trigger = ReminderTime.of(21, 0).nextTriggerAfter(now, STOCKHOLM);
        assertEquals(at(STOCKHOLM, 2026, 10, 13, 21, 0), trigger);
    }

    @Test
    public void nextTrigger_acrossMidnightAndMonthAndYear() {
        long now = at(STOCKHOLM, 2026, 12, 31, 23, 30);
        long trigger = ReminderTime.of(0, 15).nextTriggerAfter(now, STOCKHOLM);
        assertEquals(at(STOCKHOLM, 2027, 1, 1, 0, 15), trigger);
    }

    @Test
    public void nextTrigger_springForward_keepsLocalTime() {
        // Europe/Stockholm switches to summer time on 2026-03-29 at 02:00 -> 03:00.
        long firedAt = at(STOCKHOLM, 2026, 3, 28, 21, 0);
        long next = ReminderTime.of(21, 0).nextTriggerAfter(firedAt, STOCKHOLM);
        assertEquals(at(STOCKHOLM, 2026, 3, 29, 21, 0), next);
        assertEquals(23 * HOUR, next - firedAt); // A fixed 24h repeat would drift to 22:00.
    }

    @Test
    public void nextTrigger_fallBack_keepsLocalTime() {
        // Europe/Stockholm returns to standard time on 2026-10-25 at 03:00 -> 02:00.
        long firedAt = at(STOCKHOLM, 2026, 10, 24, 21, 0);
        long next = ReminderTime.of(21, 0).nextTriggerAfter(firedAt, STOCKHOLM);
        assertEquals(at(STOCKHOLM, 2026, 10, 25, 21, 0), next);
        assertEquals(25 * HOUR, next - firedAt);
    }

    @Test
    public void nextTrigger_timeInsideSpringForwardGap_firesAfterGap() {
        long now = at(STOCKHOLM, 2026, 3, 28, 22, 0);
        long trigger = ReminderTime.of(2, 30).nextTriggerAfter(now, STOCKHOLM);
        assertEquals(at(STOCKHOLM, 2026, 3, 29, 3, 30), trigger);
        assertTrue(trigger > now);
    }

    @Test
    public void nextTrigger_dependsOnTimeZone() {
        long now = at(STOCKHOLM, 2026, 10, 13, 10, 0);
        long stockholm = ReminderTime.of(21, 0).nextTriggerAfter(now, STOCKHOLM);
        long newYork = ReminderTime.of(21, 0).nextTriggerAfter(now, NEW_YORK);
        assertEquals(6 * HOUR, newYork - stockholm);
    }

    @Test
    public void nextTrigger_alwaysWithinOneDayAndInFuture() {
        ReminderTime time = ReminderTime.of(7, 45);
        long start = at(NEW_YORK, 2026, 1, 1, 0, 0);
        for (int i = 0; i < 24 * 400; i++) {
            long now = start + i * HOUR + 17_000L;
            long trigger = time.nextTriggerAfter(now, NEW_YORK);
            assertTrue(trigger > now);
            assertTrue(trigger - now <= 25 * HOUR);
            Calendar c = Calendar.getInstance(NEW_YORK);
            c.setTimeInMillis(trigger);
            assertEquals(7, c.get(Calendar.HOUR_OF_DAY));
            assertEquals(45, c.get(Calendar.MINUTE));
        }
    }

    @Test
    public void parseLegacy_usEnglish() {
        assertEquals(ReminderTime.of(21, 30), ReminderTime.parseLegacy("09:30 PM", Locale.US));
        assertEquals(ReminderTime.of(0, 0), ReminderTime.parseLegacy("12:00 AM", Locale.US));
        assertEquals(ReminderTime.of(12, 15), ReminderTime.parseLegacy("12:15 PM", Locale.US));
        assertEquals(ReminderTime.of(7, 5), ReminderTime.parseLegacy(" 07:05 AM ", Locale.US));
    }

    @Test
    public void parseLegacy_fallsBackToUsWhenDeviceLocaleChanged() {
        assertEquals(ReminderTime.of(21, 30),
                ReminderTime.parseLegacy("09:30 PM", Locale.forLanguageTag("sv-SE")));
    }

    @Test
    public void parseLegacy_twentyFourHourText() {
        assertEquals(ReminderTime.of(21, 30), ReminderTime.parseLegacy("21:30", Locale.US));
    }

    @Test
    public void parseLegacy_invalidInput_returnsNull() {
        assertNull(ReminderTime.parseLegacy(null, Locale.US));
        assertNull(ReminderTime.parseLegacy("", Locale.US));
        assertNull(ReminderTime.parseLegacy("   ", Locale.US));
        assertNull(ReminderTime.parseLegacy("garbage", Locale.US));
        assertNull(ReminderTime.parseLegacy("09:30 PM extra", Locale.US));
        assertNull(ReminderTime.parseLegacy("13:30 PM", Locale.US));
        assertNull(ReminderTime.parseLegacy("25:00", Locale.US));
    }

    @Test
    public void parseLegacy_roundTripsVersion1FormatInEveryLocale() {
        // Version 1.0 formatted with new SimpleDateFormat("hh:mm a") in the device locale.
        List<String> failures = new ArrayList<>();
        int[][] samples = {{0, 0}, {9, 5}, {12, 30}, {21, 45}};
        for (Locale locale : Locale.getAvailableLocales()) {
            for (int[] sample : samples) {
                SimpleDateFormat v1 = new SimpleDateFormat("hh:mm a", locale);
                v1.setTimeZone(TimeZone.getTimeZone("UTC"));
                Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                c.clear();
                c.set(2025, 6, 1, sample[0], sample[1]);
                String stored = v1.format(c.getTime());
                ReminderTime parsed = ReminderTime.parseLegacy(stored, locale);
                if (!ReminderTime.of(sample[0], sample[1]).equals(parsed)) {
                    failures.add(locale.toLanguageTag() + " '" + stored + "' -> " + parsed);
                }
            }
        }
        assertTrue("Unparsed: " + failures, failures.isEmpty());
    }

    @Test
    public void toString_isZeroPadded() {
        assertNotNull(ReminderTime.of(7, 5).toString());
        assertEquals("07:05", ReminderTime.of(7, 5).toString());
    }
}
