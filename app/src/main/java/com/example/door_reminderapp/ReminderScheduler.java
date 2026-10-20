package com.example.door_reminderapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.AlarmManagerCompat;

import java.util.TimeZone;

/**
 * Schedules the daily reminder with {@link AlarmManager}, without the exact-alarm permission.
 *
 * <p>Two alarms are set for the same moment:
 * <ul>
 *   <li>a windowed alarm ({@link AlarmManager#setWindow}) that Android delivers within
 *   {@link #DELIVERY_WINDOW_MS} while the device is not in deep idle, and</li>
 *   <li>a Doze-tolerant alarm ({@code setAndAllowWhileIdle}) as a backup; on Android 12+ the
 *   system may defer it by up to an hour, which was observed on a real device.</li>
 * </ul>
 * Whichever fires first shows the notification and reschedules both for the next day, which
 * replaces the other pending alarm. The stored next trigger time lets the receiver ignore a
 * late duplicate. Rescheduling every day keeps the chosen local time across DST and time-zone
 * changes.
 */
public final class ReminderScheduler {

    /**
     * Request code and intent of the backup alarm must stay identical to version 1.x so that its
     * repeating alarm is replaced (or cancelled) instead of firing alongside the new ones.
     */
    static final int REQUEST_CODE = 0;
    static final int REQUEST_CODE_WINDOW = 1;
    /** Android 12+ enforces a 10-minute minimum for windowed alarms. */
    static final long DELIVERY_WINDOW_MS = 10L * 60L * 1000L;
    /** An alarm arriving this long before the stored trigger belongs to an old occurrence. */
    static final long DUPLICATE_TOLERANCE_MS = 60L * 1000L;

    private ReminderScheduler() {
    }

    /** Makes the scheduled alarms match the stored reminder setting. */
    public static void sync(@NonNull Context context) {
        ReminderTime time = DoorStateStore.from(context).getReminder();
        if (time == null) {
            cancel(context);
        } else {
            schedule(context, time);
        }
    }

    /** Schedules the next occurrence of {@code time}, replacing any existing reminder alarms. */
    public static long schedule(@NonNull Context context, @NonNull ReminderTime time) {
        return schedule(context, time, System.currentTimeMillis(), TimeZone.getDefault());
    }

    static long schedule(@NonNull Context context, @NonNull ReminderTime time, long nowMillis,
                         @NonNull TimeZone timeZone) {
        AlarmManager alarmManager = alarmManager(context);
        if (alarmManager == null) return -1L;
        long triggerAt = time.nextTriggerAfter(nowMillis, timeZone);
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, DELIVERY_WINDOW_MS,
                pendingIntent(context, REQUEST_CODE_WINDOW));
        AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, triggerAt,
                pendingIntent(context, REQUEST_CODE));
        DoorStateStore.from(context).setNextTriggerAt(triggerAt);
        return triggerAt;
    }

    public static void cancel(@NonNull Context context) {
        AlarmManager alarmManager = alarmManager(context);
        for (int requestCode : new int[]{REQUEST_CODE, REQUEST_CODE_WINDOW}) {
            PendingIntent pendingIntent = pendingIntent(context, requestCode);
            if (alarmManager != null) alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
        DoorStateStore.from(context).clearNextTriggerAt();
    }

    /** True if an alarm delivered now is a late duplicate of an occurrence already handled. */
    static boolean isStaleDelivery(@NonNull Context context, long nowMillis) {
        long nextTrigger = DoorStateStore.from(context).getNextTriggerAt();
        return nextTrigger > 0 && nowMillis < nextTrigger - DUPLICATE_TOLERANCE_MS;
    }

    @NonNull
    static PendingIntent pendingIntent(@NonNull Context context, int requestCode) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Nullable
    private static AlarmManager alarmManager(@NonNull Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }
}
