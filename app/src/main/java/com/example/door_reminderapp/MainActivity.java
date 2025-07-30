package com.example.door_reminderapp;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.UserMessagingPlatform;
import com.google.android.ump.FormError;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView statusText, timeText, reminderText;
    private SwitchCompat switchLock;
    private SharedPreferences prefs;

    private ConsentInformation consentInformation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // GDPR Consent first
        requestGDPRConsent();

        setContentView(R.layout.activity_main);

        // Handle notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        switchLock = findViewById(R.id.switchLock);
        statusText = findViewById(R.id.statusText);
        timeText = findViewById(R.id.timeText);
        reminderText = findViewById(R.id.reminderText);

        Button btnSetReminder = findViewById(R.id.btnSetReminder);
        btnSetReminder.setOnClickListener(v -> showTimePicker());

        Button btnDeleteReminder = findViewById(R.id.btnDeleteReminder);
        btnDeleteReminder.setOnClickListener(v -> deleteReminder());

        prefs = getSharedPreferences("SwitchPrefs", MODE_PRIVATE);
        boolean isLocked = prefs.getBoolean("isLocked", false);
        String savedTime = prefs.getString("last_checked_time", "--:--");

        String savedReminderTime = prefs.getString("reminder_time", null);
        if (savedReminderTime != null) {
            updateReminderText(savedReminderTime);
        } else {
            reminderText.setText("");
        }

        switchLock.setChecked(isLocked);
        updateUI(isLocked, savedTime);
        showTransparentPopup(isLocked);

        switchLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String currentTime = getCurrentTime();

            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("isLocked", isChecked);
            editor.putString("last_checked_time", currentTime);
            editor.apply();

            updateUI(isChecked, currentTime);
            showTransparentPopup(isChecked);
        });
    }

    // GDPR Consent (UMP SDK)
    private void requestGDPRConsent() {
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();

        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        consentInformation.requestConsentInfoUpdate(
                this,
                params,
                () -> {
                    if (consentInformation.isConsentFormAvailable()) {
                        loadConsentForm();
                    } else {
                        initAdMob(); // Safe to load ads
                    }
                },
                formError -> {
                    initAdMob(); // Even on failure, continue
                }
        );
    }

    private void loadConsentForm() {
        UserMessagingPlatform.loadConsentForm(
                this,
                consentForm -> {
                    if (consentInformation.getConsentStatus() == ConsentInformation.ConsentStatus.REQUIRED) {
                        consentForm.show(
                                this,
                                formError -> initAdMob() // Continue regardless
                        );
                    } else {
                        initAdMob(); // Consent not required
                    }
                },
                formError -> initAdMob() // Load failed, continue
        );
    }

    // Initialize AdMob after consent
    private void initAdMob() {
        MobileAds.initialize(this);
        AdView adView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    private void showTimePicker() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePicker = new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
            Calendar reminderTime = Calendar.getInstance();
            reminderTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
            reminderTime.set(Calendar.MINUTE, minuteOfHour);
            reminderTime.set(Calendar.SECOND, 0);

            scheduleReminder(reminderTime);
        }, hour, minute, false);

        timePicker.show();
    }

    private void scheduleReminder(Calendar reminderTime) {
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                reminderTime.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );

        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");
        String formattedTime = sdf.format(reminderTime.getTime());

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("reminder_time", formattedTime);
        editor.apply();

        updateReminderText(formattedTime);
    }

    private void updateReminderText(String time) {
        reminderText.setText("Reminder set for: " + time);
        Drawable icon = getResources().getDrawable(R.drawable.baseline_more_time_24, getTheme());
        reminderText.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        reminderText.setCompoundDrawablePadding(12);
    }

    @SuppressLint("SetTextI18n")
    private void updateUI(boolean isLocked, String time) {
        Drawable icon;

        if (isLocked) {
            icon = getResources().getDrawable(R.drawable.baseline_lock_24);
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            statusText.setCompoundDrawables(icon, null, null, null);
            statusText.setText(" Locked");
            switchLock.setTrackTintList(ColorStateList.valueOf(0xFF66BB6A));
            switchLock.setThumbTintList(ColorStateList.valueOf(0xFF66BB6A));
        } else {
            icon = getResources().getDrawable(R.drawable.baseline_lock_open_24);
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            statusText.setCompoundDrawables(icon, null, null, null);
            statusText.setText(" Unlocked");
            switchLock.setTrackTintList(ColorStateList.valueOf(0xFFEF5350));
            switchLock.setThumbTintList(ColorStateList.valueOf(0xFFEF5350));
        }

        timeText.setText("Checked at: " + time);
        Drawable clockIcon = getResources().getDrawable(R.drawable.baseline_access_time_24, getTheme());
        timeText.setCompoundDrawablesWithIntrinsicBounds(clockIcon, null, null, null);
        timeText.setCompoundDrawablePadding(12);
    }

    private void showTransparentPopup(boolean isLocked) {
        String message = isLocked ? "Door is LOCKED" : "Door is UNLOCKED";

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_popup);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView popupMessage = dialog.findViewById(R.id.popupMessage);
        popupMessage.setText(message);

        Drawable icon = getResources().getDrawable(
                isLocked ? R.drawable.baseline_lock_24 : R.drawable.baseline_lock_open_24,
                getTheme()
        );
        popupMessage.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        popupMessage.setCompoundDrawablePadding(16);

        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.gravity = Gravity.CENTER;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(params);

        dialog.show();
        new Handler().postDelayed(dialog::dismiss, 4000);
    }

    private void deleteReminder() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("reminder_time");
        editor.apply();

        reminderText.setText("");
        reminderText.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("EEE, MMM d, yyyy, hh:mm a", Locale.getDefault()).format(new Date());
    }
}
