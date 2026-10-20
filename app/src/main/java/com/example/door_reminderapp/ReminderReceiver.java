package com.example.door_reminderapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives the daily reminder alarm, shows the notification and schedules the next day. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        DoorStateStore store = DoorStateStore.from(context);
        ReminderTime time = store.getReminder();
        if (time == null) {
            // Reminder was turned off; make sure no stale alarm (e.g. from version 1.0) remains.
            ReminderScheduler.cancel(context);
            return;
        }
        if (ReminderScheduler.isStaleDelivery(context, System.currentTimeMillis())) {
            // The twin alarm for this occurrence already fired and rescheduled both.
            return;
        }
        ReminderNotifications.show(context, store.getState());
        ReminderScheduler.schedule(context, time);
    }
}
