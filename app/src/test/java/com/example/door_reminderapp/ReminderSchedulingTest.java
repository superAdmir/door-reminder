package com.example.door_reminderapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.List;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
public class ReminderSchedulingTest {

    private Application context;
    private AlarmManager alarmManager;
    private ShadowAlarmManager shadowAlarms;
    private NotificationManager notificationManager;
    private DoorStateStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(DoorStateStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        shadowAlarms = shadowOf(alarmManager);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        store = DoorStateStore.from(context);
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    private List<ShadowAlarmManager.ScheduledAlarm> alarms() {
        return shadowAlarms.getScheduledAlarms();
    }

    @Test
    public void schedule_setsWindowedAndDozeBackupAlarmsAtNextOccurrence() {
        TimeZone zone = TimeZone.getTimeZone("Europe/Stockholm");
        long now = 1_760_000_000_000L;
        ReminderTime time = ReminderTime.of(21, 0);

        long trigger = ReminderScheduler.schedule(context, time, now, zone);

        assertEquals(time.nextTriggerAfter(now, zone), trigger);
        assertEquals(2, alarms().size());
        int windowed = 0;
        int backup = 0;
        for (ShadowAlarmManager.ScheduledAlarm alarm : alarms()) {
            assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType());
            assertEquals(trigger, alarm.getTriggerAtMs());
            assertEquals(0L, alarm.getIntervalMs());
            if (alarm.isAllowWhileIdle()) {
                backup++;
            } else {
                assertEquals(ReminderScheduler.DELIVERY_WINDOW_MS, alarm.getWindowLengthMs());
                windowed++;
            }
        }
        assertEquals(1, windowed);
        assertEquals(1, backup);
        assertEquals(trigger, store.getNextTriggerAt());
    }

    @Test
    public void schedule_twice_replacesInsteadOfDuplicating() {
        ReminderScheduler.schedule(context, ReminderTime.of(8, 0));
        ReminderScheduler.schedule(context, ReminderTime.of(22, 15));
        assertEquals(2, alarms().size());
    }

    @Test
    public void schedule_replacesRepeatingAlarmCreatedByVersion1() {
        PendingIntent legacy = PendingIntent.getBroadcast(context, 0,
                new Intent(context, ReminderReceiver.class), PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, 123L, AlarmManager.INTERVAL_DAY, legacy);
        assertEquals(1, alarms().size());

        ReminderScheduler.schedule(context, ReminderTime.of(21, 0));

        assertEquals(2, alarms().size());
        for (ShadowAlarmManager.ScheduledAlarm alarm : alarms()) {
            assertEquals(0L, alarm.getIntervalMs());
        }
    }

    @Test
    public void sync_withoutReminder_cancelsVersion1Alarm() {
        PendingIntent legacy = PendingIntent.getBroadcast(context, 0,
                new Intent(context, ReminderReceiver.class), PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, 123L, AlarmManager.INTERVAL_DAY, legacy);

        ReminderScheduler.sync(context);

        assertTrue(alarms().isEmpty());
    }

    @Test
    public void sync_migratesVersion1ReminderAndSchedulesIt() {
        context.getSharedPreferences(DoorStateStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString("reminder_time", "09:30 PM").commit();

        ReminderScheduler.sync(context);

        assertEquals(2, alarms().size());
        assertEquals(ReminderTime.of(21, 30), store.getReminder());
    }

    @Test
    public void cancel_removesAlarm() {
        ReminderScheduler.schedule(context, ReminderTime.of(21, 0));
        ReminderScheduler.cancel(context);
        assertTrue(alarms().isEmpty());
        assertEquals(-1L, store.getNextTriggerAt());
    }

    @Test
    public void pendingIntent_isImmutableAndTargetsReceiverExplicitly() {
        PendingIntent pendingIntent = ReminderScheduler.pendingIntent(context, ReminderScheduler.REQUEST_CODE);
        ShadowPendingIntent shadow = shadowOf(pendingIntent);
        assertTrue(shadow.isBroadcast());
        assertTrue((shadow.getFlags() & PendingIntent.FLAG_IMMUTABLE) != 0);
        Intent saved = shadow.getSavedIntent();
        assertEquals(ReminderReceiver.class.getName(), saved.getComponent().getClassName());
        assertEquals(ReminderScheduler.REQUEST_CODE, shadow.getRequestCode());
    }

    @Test
    public void receiver_postsNotificationAndSchedulesNextDay() {
        store.setReminder(ReminderTime.of(21, 0));
        store.setLocked(false, System.currentTimeMillis());

        new ReminderReceiver().onReceive(context, new Intent(context, ReminderReceiver.class));

        List<Notification> posted = shadowOf(notificationManager).getAllNotifications();
        assertEquals(1, posted.size());
        Notification notification = posted.get(0);
        assertEquals(ReminderNotifications.CHANNEL_ID, notification.getChannelId());
        assertNotNull(notification.contentIntent);
        assertTrue((notification.flags & Notification.FLAG_AUTO_CANCEL) != 0);
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility);
        assertNotNull(notification.publicVersion);
        assertEquals(context.getString(R.string.notification_text_unlocked),
                shadowOf(notification).getContentText().toString());

        assertEquals(2, alarms().size());
        assertTrue(alarms().get(0).getTriggerAtMs() > System.currentTimeMillis());
    }

    @Test
    public void receiver_twinAlarmForSameOccurrence_doesNotNotifyTwice() {
        store.setReminder(ReminderTime.of(21, 0));
        Intent alarm = new Intent(context, ReminderReceiver.class);

        new ReminderReceiver().onReceive(context, alarm); // first alarm: notifies, reschedules
        notificationManager.cancelAll();
        new ReminderReceiver().onReceive(context, alarm); // late twin for the same occurrence

        assertTrue(shadowOf(notificationManager).getAllNotifications().isEmpty());
        assertEquals(2, alarms().size());
    }

    @Test
    public void receiver_withoutNotificationPermission_stillReschedules() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        store.setReminder(ReminderTime.of(21, 0));

        new ReminderReceiver().onReceive(context, new Intent(context, ReminderReceiver.class));

        assertTrue(shadowOf(notificationManager).getAllNotifications().isEmpty());
        assertEquals(2, alarms().size());
    }

    @Test
    public void receiver_withBlockedChannel_postsNothing() {
        ReminderNotifications.ensureChannel(context);
        NotificationChannel channel = notificationManager.getNotificationChannel(ReminderNotifications.CHANNEL_ID);
        channel.setImportance(NotificationManager.IMPORTANCE_NONE);
        notificationManager.createNotificationChannel(channel);
        store.setReminder(ReminderTime.of(21, 0));

        assertFalse(ReminderNotifications.canPost(context));
        new ReminderReceiver().onReceive(context, new Intent(context, ReminderReceiver.class));
        assertTrue(shadowOf(notificationManager).getAllNotifications().isEmpty());
    }

    @Test
    public void receiver_afterReminderTurnedOff_doesNothingAndClearsAlarm() {
        ReminderScheduler.schedule(context, ReminderTime.of(21, 0));
        store.clearReminder();

        new ReminderReceiver().onReceive(context, new Intent(context, ReminderReceiver.class));

        assertTrue(shadowOf(notificationManager).getAllNotifications().isEmpty());
        assertTrue(alarms().isEmpty());
    }

    @Test
    public void notificationText_reflectsRecordedStatus() {
        assertEquals(context.getString(R.string.notification_text_no_record),
                ReminderNotifications.notificationText(context, DoorState.empty()));
        DoorState locked = store.setLocked(true, System.currentTimeMillis());
        assertTrue(ReminderNotifications.notificationText(context, locked)
                .startsWith("You marked it as locked"));
    }

    @Test
    public void channel_keepsVersion1IdAndHasReadableName() {
        ReminderNotifications.ensureChannel(context);
        NotificationChannel channel = notificationManager.getNotificationChannel("door_lock_reminder");
        assertNotNull(channel);
        assertEquals(context.getString(R.string.notification_channel_name), channel.getName().toString());
    }

    @Test
    public void rescheduleReceiver_restoresAlarmAfterBootAndTimeChanges() {
        store.setReminder(ReminderTime.of(6, 30));
        String[] actions = {Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED};
        for (String action : actions) {
            ReminderScheduler.cancel(context);
            new RescheduleReceiver().onReceive(context, new Intent(action));
            assertEquals(action, 2, alarms().size());
        }
    }

    @Test
    public void rescheduleReceiver_ignoresUnexpectedActionsAndNullIntent() {
        store.setReminder(ReminderTime.of(6, 30));
        new RescheduleReceiver().onReceive(context, new Intent("com.example.SPOOF"));
        new RescheduleReceiver().onReceive(context, null);
        assertTrue(alarms().isEmpty());
    }

    @Test
    public void rescheduleReceiver_withReminderOff_leavesNoAlarm() {
        new RescheduleReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertTrue(alarms().isEmpty());
        assertNull(store.getReminder());
    }
}
