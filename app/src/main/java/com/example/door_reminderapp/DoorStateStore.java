package com.example.door_reminderapp;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Local persistence for the door status and the reminder time.
 *
 * <p>The preference file and the {@code isLocked} key are the ones used by version 1.0, so an
 * upgrade keeps the user's data. Values written by 1.0 as display strings are migrated to
 * structured values the first time they are read. Corrupt values never crash the app; they are
 * treated as missing.
 */
public final class DoorStateStore {

    static final String PREFS_NAME = "SwitchPrefs";

    static final String KEY_LOCKED = "isLocked";
    static final String KEY_CHANGED_AT = "last_changed_at";
    static final String KEY_REMINDER_HOUR = "reminder_hour";
    static final String KEY_REMINDER_MINUTE = "reminder_minute";
    static final String KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked";
    static final String KEY_NEXT_TRIGGER_AT = "reminder_next_trigger_at";

    // Display strings stored by version 1.0.
    static final String KEY_LEGACY_CHANGED_AT_TEXT = "last_checked_time";
    static final String KEY_LEGACY_REMINDER_TEXT = "reminder_time";
    static final String LEGACY_CHANGED_AT_PATTERN = "EEE, MMM d, yyyy, hh:mm a";
    private static final String LEGACY_EMPTY_TIME = "--:--";

    private final SharedPreferences prefs;
    private final Locale deviceLocale;
    private final TimeZone timeZone;

    public DoorStateStore(@NonNull SharedPreferences prefs, @NonNull Locale deviceLocale,
                          @NonNull TimeZone timeZone) {
        this.prefs = prefs;
        this.deviceLocale = deviceLocale;
        this.timeZone = timeZone;
    }

    @NonNull
    public static DoorStateStore from(@NonNull Context context) {
        return new DoorStateStore(
                context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                Locale.getDefault(),
                TimeZone.getDefault());
    }

    @NonNull
    public DoorState getState() {
        migrateLegacyTimestamp();
        boolean hasRecord = prefs.contains(KEY_LOCKED) || prefs.contains(KEY_CHANGED_AT)
                || prefs.contains(KEY_LEGACY_CHANGED_AT_TEXT);
        if (!hasRecord) return DoorState.empty();

        boolean locked = readBoolean(KEY_LOCKED, false);
        long changedAt = readLong(KEY_CHANGED_AT, DoorState.UNKNOWN_TIME);
        if (changedAt < 0) changedAt = DoorState.UNKNOWN_TIME;
        String legacyText = changedAt == DoorState.UNKNOWN_TIME ? readLegacyChangedAtText() : null;
        return new DoorState(true, locked, changedAt, legacyText);
    }

    /** Records a status change and returns the new state. */
    @NonNull
    public DoorState setLocked(boolean locked, long nowMillis) {
        prefs.edit()
                .putBoolean(KEY_LOCKED, locked)
                .putLong(KEY_CHANGED_AT, nowMillis)
                .remove(KEY_LEGACY_CHANGED_AT_TEXT)
                .apply();
        return new DoorState(true, locked, nowMillis, null);
    }

    /** Restores a previously read state exactly, e.g. to undo an accidental change. */
    public void restore(@NonNull DoorState state) {
        SharedPreferences.Editor editor = prefs.edit();
        if (!state.hasRecord) {
            editor.remove(KEY_LOCKED).remove(KEY_CHANGED_AT).remove(KEY_LEGACY_CHANGED_AT_TEXT);
        } else {
            editor.putBoolean(KEY_LOCKED, state.locked);
            if (state.hasChangedAtTime()) {
                editor.putLong(KEY_CHANGED_AT, state.changedAtMillis);
            } else {
                editor.remove(KEY_CHANGED_AT);
            }
            if (state.legacyChangedAtText != null) {
                editor.putString(KEY_LEGACY_CHANGED_AT_TEXT, state.legacyChangedAtText);
            } else {
                editor.remove(KEY_LEGACY_CHANGED_AT_TEXT);
            }
        }
        editor.apply();
    }

    /**
     * Returns the configured daily reminder, or {@code null} if reminders are off. A reminder
     * saved by version 1.0 is migrated; if it cannot be understood it is removed so the UI never
     * shows a reminder that is not actually scheduled.
     */
    @Nullable
    public ReminderTime getReminder() {
        int hour = readInt(KEY_REMINDER_HOUR, -1);
        int minute = readInt(KEY_REMINDER_MINUTE, -1);
        if (ReminderTime.isValid(hour, minute)) {
            return ReminderTime.of(hour, minute);
        }
        if (prefs.contains(KEY_REMINDER_HOUR) || prefs.contains(KEY_REMINDER_MINUTE)) {
            prefs.edit().remove(KEY_REMINDER_HOUR).remove(KEY_REMINDER_MINUTE).apply();
        }
        if (!prefs.contains(KEY_LEGACY_REMINDER_TEXT)) return null;

        ReminderTime legacy = ReminderTime.parseLegacy(readString(KEY_LEGACY_REMINDER_TEXT), deviceLocale);
        SharedPreferences.Editor editor = prefs.edit().remove(KEY_LEGACY_REMINDER_TEXT);
        if (legacy != null) {
            editor.putInt(KEY_REMINDER_HOUR, legacy.hour).putInt(KEY_REMINDER_MINUTE, legacy.minute);
        }
        editor.apply();
        return legacy;
    }

    public void setReminder(@NonNull ReminderTime time) {
        prefs.edit()
                .putInt(KEY_REMINDER_HOUR, time.hour)
                .putInt(KEY_REMINDER_MINUTE, time.minute)
                .remove(KEY_LEGACY_REMINDER_TEXT)
                .apply();
    }

    public void clearReminder() {
        prefs.edit()
                .remove(KEY_REMINDER_HOUR)
                .remove(KEY_REMINDER_MINUTE)
                .remove(KEY_LEGACY_REMINDER_TEXT)
                .apply();
    }

    /** When the scheduled reminder alarms are due, or -1 if none are scheduled. */
    public long getNextTriggerAt() {
        return readLong(KEY_NEXT_TRIGGER_AT, -1L);
    }

    void setNextTriggerAt(long triggerAtMillis) {
        prefs.edit().putLong(KEY_NEXT_TRIGGER_AT, triggerAtMillis).apply();
    }

    void clearNextTriggerAt() {
        prefs.edit().remove(KEY_NEXT_TRIGGER_AT).apply();
    }

    public boolean wasNotificationPermissionAsked() {
        return readBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false);
    }

    public void setNotificationPermissionAsked() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true).apply();
    }

    /** Converts the 1.0 display string into a timestamp when it can be parsed reliably. */
    private void migrateLegacyTimestamp() {
        if (prefs.contains(KEY_CHANGED_AT) || !prefs.contains(KEY_LEGACY_CHANGED_AT_TEXT)) return;
        String text = readLegacyChangedAtText();
        if (text == null) {
            prefs.edit().remove(KEY_LEGACY_CHANGED_AT_TEXT).apply();
            return;
        }
        long parsed = parseLegacyTimestamp(text, deviceLocale, timeZone);
        if (parsed != DoorState.UNKNOWN_TIME) {
            prefs.edit()
                    .putLong(KEY_CHANGED_AT, parsed)
                    .remove(KEY_LEGACY_CHANGED_AT_TEXT)
                    .apply();
        }
    }

    static long parseLegacyTimestamp(@NonNull String text, @NonNull Locale deviceLocale,
                                     @NonNull TimeZone timeZone) {
        Set<Locale> locales = new LinkedHashSet<>();
        locales.add(deviceLocale);
        locales.add(Locale.US);
        for (Locale locale : locales) {
            SimpleDateFormat format = new SimpleDateFormat(LEGACY_CHANGED_AT_PATTERN, locale);
            format.setLenient(false);
            format.setTimeZone(timeZone);
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(text.trim(), position);
            if (date != null && position.getIndex() == text.trim().length()) {
                return date.getTime();
            }
        }
        return DoorState.UNKNOWN_TIME;
    }

    @Nullable
    private String readLegacyChangedAtText() {
        String text = readString(KEY_LEGACY_CHANGED_AT_TEXT);
        if (text == null || text.trim().isEmpty() || LEGACY_EMPTY_TIME.equals(text.trim())) {
            return null;
        }
        return text;
    }

    private boolean readBoolean(String key, boolean fallback) {
        try {
            return prefs.getBoolean(key, fallback);
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    private long readLong(String key, long fallback) {
        try {
            return prefs.getLong(key, fallback);
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    private int readInt(String key, int fallback) {
        try {
            return prefs.getInt(key, fallback);
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    @Nullable
    private String readString(String key) {
        try {
            return prefs.getString(key, null);
        } catch (ClassCastException e) {
            return null;
        }
    }
}
