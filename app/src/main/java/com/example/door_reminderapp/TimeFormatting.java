package com.example.door_reminderapp;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;

import java.util.Calendar;

/** Formats times using the device locale, time zone and 12/24-hour preference. */
final class TimeFormatting {

    private TimeFormatting() {
    }

    @NonNull
    static String timeOfDay(@NonNull Context context, @NonNull ReminderTime time) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, time.hour);
        calendar.set(Calendar.MINUTE, time.minute);
        return DateFormat.getTimeFormat(context).format(calendar.getTime());
    }

    /** E.g. "Mon, Oct 13, 9:03 PM"; the year is added only when it is not the current year. */
    @NonNull
    static String dateTime(@NonNull Context context, long millis) {
        return DateUtils.formatDateTime(context, millis,
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY
                        | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH
                        | DateUtils.FORMAT_SHOW_TIME);
    }

    /** E.g. "Just now", "5 minutes ago"; empty for future timestamps (clock was changed). */
    @NonNull
    static String relative(@NonNull Context context, long millis, long nowMillis) {
        if (millis > nowMillis) return "";
        if (nowMillis - millis < DateUtils.MINUTE_IN_MILLIS) {
            return context.getString(R.string.status_changed_just_now);
        }
        return DateUtils.getRelativeTimeSpanString(millis, nowMillis, DateUtils.MINUTE_IN_MILLIS)
                .toString();
    }
}
