package com.example.door_reminderapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
public class DoorStateStoreTest {

    private static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Stockholm");

    private SharedPreferences prefs;
    private DoorStateStore store;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences(DoorStateStore.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        store = new DoorStateStore(prefs, Locale.US, ZONE);
    }

    @Test
    public void firstLaunch_hasNoRecordAndNoReminder() {
        DoorState state = store.getState();
        assertFalse(state.hasRecord);
        assertFalse(state.locked);
        assertFalse(state.hasChangedAtTime());
        assertNull(store.getReminder());
    }

    @Test
    public void setLocked_persistsStatusAndTimestampTogether() {
        DoorState written = store.setLocked(true, 1_000_000L);
        DoorState read = new DoorStateStore(prefs, Locale.US, ZONE).getState();
        assertTrue(read.hasRecord);
        assertTrue(read.locked);
        assertEquals(1_000_000L, read.changedAtMillis);
        assertEquals(written.changedAtMillis, read.changedAtMillis);

        store.setLocked(false, 2_000_000L);
        DoorState unlocked = store.getState();
        assertFalse(unlocked.locked);
        assertEquals(2_000_000L, unlocked.changedAtMillis);
    }

    @Test
    public void restore_undoesChangeIncludingFirstEverChange() {
        DoorState before = store.getState();
        store.setLocked(true, 5L);
        store.restore(before);
        assertFalse(store.getState().hasRecord);

        store.setLocked(true, 10L);
        DoorState locked = store.getState();
        store.setLocked(false, 20L);
        store.restore(locked);
        DoorState restored = store.getState();
        assertTrue(restored.locked);
        assertEquals(10L, restored.changedAtMillis);
    }

    @Test
    public void upgradeFromVersion1_migratesStatusTimestampAndReminder() {
        prefs.edit()
                .putBoolean("isLocked", true)
                .putString("last_checked_time", "Tue, Oct 13, 2026, 09:03 PM")
                .putString("reminder_time", "09:30 PM")
                .commit();

        DoorState state = store.getState();
        assertTrue(state.hasRecord);
        assertTrue(state.locked);
        Calendar expected = Calendar.getInstance(ZONE);
        expected.clear();
        expected.set(2026, Calendar.OCTOBER, 13, 21, 3, 0);
        assertEquals(expected.getTimeInMillis(), state.changedAtMillis);
        assertNull(state.legacyChangedAtText);
        assertFalse(prefs.contains("last_checked_time"));

        assertEquals(ReminderTime.of(21, 30), store.getReminder());
        assertFalse(prefs.contains("reminder_time"));
        assertEquals(21, prefs.getInt(DoorStateStore.KEY_REMINDER_HOUR, -1));
        assertEquals(30, prefs.getInt(DoorStateStore.KEY_REMINDER_MINUTE, -1));
    }

    @Test
    public void upgradeFromVersion1_withSwedishLocaleData() {
        Locale swedish = Locale.forLanguageTag("sv-SE");
        String storedTime = new java.text.SimpleDateFormat("hh:mm a", swedish)
                .format(new java.util.Date(at(2026, 10, 13, 7, 15)));
        String storedChanged = new java.text.SimpleDateFormat(
                DoorStateStore.LEGACY_CHANGED_AT_PATTERN, swedish) {{
                    setTimeZone(ZONE);
                }}.format(new java.util.Date(at(2026, 10, 13, 22, 40)));
        prefs.edit()
                .putBoolean("isLocked", false)
                .putString("last_checked_time", storedChanged)
                .putString("reminder_time", storedTime)
                .commit();

        DoorStateStore swedishStore = new DoorStateStore(prefs, swedish, ZONE);
        assertEquals(at(2026, 10, 13, 22, 40), swedishStore.getState().changedAtMillis);
        assertEquals(ReminderTime.of(7, 15), swedishStore.getReminder());
    }

    @Test
    public void upgradeFromVersion1_neverChanged_placeholderIsIgnored() {
        prefs.edit().putBoolean("isLocked", false).putString("last_checked_time", "--:--").commit();
        DoorState state = store.getState();
        assertTrue(state.hasRecord);
        assertFalse(state.hasChangedAtTime());
        assertNull(state.legacyChangedAtText);
    }

    @Test
    public void unparseableLegacyTimestamp_isKeptForDisplay() {
        prefs.edit().putBoolean("isLocked", true).putString("last_checked_time", "sometime").commit();
        DoorState state = store.getState();
        assertFalse(state.hasChangedAtTime());
        assertEquals("sometime", state.legacyChangedAtText);
    }

    @Test
    public void unparseableLegacyReminder_isRemovedSoUiMatchesAlarms() {
        prefs.edit().putString("reminder_time", "not a time").commit();
        assertNull(store.getReminder());
        assertFalse(prefs.contains("reminder_time"));
    }

    @Test
    public void corruptValues_doNotCrashAndAreTreatedAsMissing() {
        prefs.edit()
                .putString("isLocked", "yes")
                .putString(DoorStateStore.KEY_CHANGED_AT, "yesterday")
                .putString(DoorStateStore.KEY_REMINDER_HOUR, "nine")
                .putInt(DoorStateStore.KEY_REMINDER_MINUTE, 0)
                .commit();
        DoorState state = store.getState();
        assertTrue(state.hasRecord);
        assertFalse(state.locked);
        assertFalse(state.hasChangedAtTime());
        assertNull(store.getReminder());
    }

    @Test
    public void outOfRangeReminder_isDiscarded() {
        prefs.edit()
                .putInt(DoorStateStore.KEY_REMINDER_HOUR, 25)
                .putInt(DoorStateStore.KEY_REMINDER_MINUTE, 0)
                .commit();
        assertNull(store.getReminder());
        assertFalse(prefs.contains(DoorStateStore.KEY_REMINDER_HOUR));
    }

    @Test
    public void negativeTimestamp_isTreatedAsUnknown() {
        prefs.edit().putBoolean("isLocked", true).putLong(DoorStateStore.KEY_CHANGED_AT, -50L).commit();
        assertFalse(store.getState().hasChangedAtTime());
    }

    @Test
    public void setAndClearReminder() {
        store.setReminder(ReminderTime.of(6, 45));
        assertEquals(ReminderTime.of(6, 45), store.getReminder());
        store.clearReminder();
        assertNull(store.getReminder());
    }

    @Test
    public void notificationPermissionAskedFlag() {
        assertFalse(store.wasNotificationPermissionAsked());
        store.setNotificationPermissionAsked();
        assertTrue(store.wasNotificationPermissionAsked());
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(ZONE);
        c.clear();
        c.set(year, month - 1, day, hour, minute, 0);
        return c.getTimeInMillis();
    }
}
