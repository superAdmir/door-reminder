package com.example.door_reminderapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** A local wall-clock time of day (hour and minute) at which the daily reminder fires. */
public final class ReminderTime {

    public final int hour;
    public final int minute;

    private ReminderTime(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
    }

    public static boolean isValid(int hour, int minute) {
        return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
    }

    @NonNull
    public static ReminderTime of(int hour, int minute) {
        if (!isValid(hour, minute)) {
            throw new IllegalArgumentException("Invalid reminder time " + hour + ":" + minute);
        }
        return new ReminderTime(hour, minute);
    }

    /**
     * Returns the first moment strictly after {@code nowMillis} at which the local clock in
     * {@code timeZone} shows this time. The wall-clock time is recomputed for every day, so the
     * reminder stays at the chosen local time across daylight-saving changes. If the time does
     * not exist on a given day (spring-forward gap) the calendar moves it forward by the gap.
     */
    public long nextTriggerAfter(long nowMillis, @NonNull TimeZone timeZone) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(nowMillis);
        setTimeOfDay(calendar);
        if (calendar.getTimeInMillis() <= nowMillis) {
            calendar.setTimeInMillis(nowMillis);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            setTimeOfDay(calendar);
        }
        return calendar.getTimeInMillis();
    }

    private void setTimeOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    /**
     * Parses the reminder text stored by version 1.0 of the app, which saved only a display
     * string formatted with {@code "hh:mm a"} in the device locale at that time.
     *
     * @return the parsed time, or {@code null} if the text cannot be interpreted unambiguously.
     */
    @Nullable
    public static ReminderTime parseLegacy(@Nullable String text, @NonNull Locale deviceLocale) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;

        Set<Locale> locales = new LinkedHashSet<>();
        locales.add(deviceLocale);
        locales.add(Locale.US);
        for (Locale locale : locales) {
            ReminderTime parsed = parseWithPattern(trimmed, "hh:mm a", locale);
            if (parsed != null) return parsed;
        }
        return parseWithPattern(trimmed, "HH:mm", Locale.US);
    }

    @Nullable
    private static ReminderTime parseWithPattern(String text, String pattern, Locale locale) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(text, position);
        if (date == null || position.getIndex() != text.length()) return null;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTime(date);
        return of(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReminderTime)) return false;
        ReminderTime other = (ReminderTime) o;
        return hour == other.hour && minute == other.minute;
    }

    @Override
    public int hashCode() {
        return hour * 60 + minute;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }
}
