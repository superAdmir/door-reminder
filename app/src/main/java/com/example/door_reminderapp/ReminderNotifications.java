package com.example.door_reminderapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Notification channel setup and the daily reminder notification. */
final class ReminderNotifications {

    /** Same channel ID as version 1.0, so the user's channel settings are kept. */
    static final String CHANNEL_ID = "door_lock_reminder";
    static final int NOTIFICATION_ID = 1001;

    private ReminderNotifications() {
    }

    static void ensureChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        // Updates name/description only; Android keeps the user's importance and sound choices.
        manager.createNotificationChannel(channel);
    }

    /** True if a reminder notification posted now would be visible to the user. */
    static boolean canPost(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = context.getSystemService(NotificationManager.class)
                    .getNotificationChannel(CHANNEL_ID);
            return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    /** Posts the reminder if notifications are allowed; returns whether it was posted. */
    @SuppressLint("MissingPermission") // Checked by canPost().
    static boolean show(@NonNull Context context, @NonNull DoorState state) {
        ensureChannel(context);
        if (!canPost(context)) return false;

        Intent openApp = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String text = notificationText(context, state);
        // The door status is hidden on a secure lock screen; only a neutral prompt is shown.
        NotificationCompat.Builder publicVersion = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_text_public));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.brand_primary))
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion.build())
                .setContentIntent(contentIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        return true;
    }

    @NonNull
    static String notificationText(@NonNull Context context, @NonNull DoorState state) {
        if (!state.hasRecord) {
            return context.getString(R.string.notification_text_no_record);
        }
        if (state.locked) {
            if (state.hasChangedAtTime()) {
                return context.getString(R.string.notification_text_locked_at,
                        TimeFormatting.dateTime(context, state.changedAtMillis));
            }
            return context.getString(R.string.notification_text_locked);
        }
        return context.getString(R.string.notification_text_unlocked);
    }
}
