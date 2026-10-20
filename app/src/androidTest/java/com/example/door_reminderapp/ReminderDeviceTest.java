package com.example.door_reminderapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Runs scheduling, notification and status persistence against a real Android runtime. */
@RunWith(AndroidJUnit4.class)
public class ReminderDeviceTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences(DoorStateStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        ReminderScheduler.cancel(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @After
    public void tearDown() {
        ReminderScheduler.cancel(context);
        context.getSystemService(NotificationManager.class).cancelAll();
    }

    private boolean alarmPendingIntentExists() {
        return PendingIntent.getBroadcast(context, ReminderScheduler.REQUEST_CODE,
                new Intent(context, ReminderReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null;
    }

    @Test
    public void scheduleAndCancel_registerAndRemoveTheAlarm() {
        long trigger = ReminderScheduler.schedule(context, ReminderTime.of(21, 0));
        assertTrue(trigger > System.currentTimeMillis());
        assertTrue(alarmPendingIntentExists());

        ReminderScheduler.cancel(context);
        assertFalse(alarmPendingIntentExists());
    }

    @Test
    public void receiver_postsVisibleNotificationAndReschedules() {
        DoorStateStore store = DoorStateStore.from(context);
        store.setReminder(ReminderTime.of(21, 0));
        store.setLocked(true, System.currentTimeMillis());

        new ReminderReceiver().onReceive(context, new Intent(context, ReminderReceiver.class));

        StatusBarNotification posted = null;
        for (StatusBarNotification n : context.getSystemService(NotificationManager.class).getActiveNotifications()) {
            if (n.getId() == ReminderNotifications.NOTIFICATION_ID) posted = n;
        }
        assertNotNull(posted);
        assertEquals(ReminderNotifications.CHANNEL_ID, posted.getNotification().getChannelId());
        assertTrue(alarmPendingIntentExists());
    }

    @Test
    public void mainActivity_togglePersistsAndSurvivesRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                TextView title = activity.findViewById(R.id.statusTitle);
                assertEquals(activity.getString(R.string.status_unknown), title.getText().toString());
                activity.findViewById(R.id.toggleButton).performClick();
                assertEquals(activity.getString(R.string.status_locked), title.getText().toString());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                TextView title = activity.findViewById(R.id.statusTitle);
                assertEquals(activity.getString(R.string.status_locked), title.getText().toString());
                activity.findViewById(R.id.toggleButton).performClick();
                assertEquals(activity.getString(R.string.status_unlocked), title.getText().toString());
            });
        }
        DoorState state = DoorStateStore.from(context).getState();
        assertTrue(state.hasRecord);
        assertFalse(state.locked);
        assertTrue(state.hasChangedAtTime());
        assertNull(state.legacyChangedAtText);
    }
}
