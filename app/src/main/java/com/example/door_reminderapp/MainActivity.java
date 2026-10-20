package com.example.door_reminderapp;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.door_reminderapp.databinding.ActivityMainBinding;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;


public class MainActivity extends AppCompatActivity implements AdsController.Listener {

    private static final String TIME_PICKER_TAG = "reminder_time_picker";
    private static final ReminderTime DEFAULT_REMINDER = ReminderTime.of(21, 0);

    private ActivityMainBinding binding;
    private DoorStateStore store;
    private AdsController ads;
    private DoorState state = DoorState.empty();

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> renderReminder());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyWindowInsets();

        store = DoorStateStore.from(this);
        ReminderNotifications.ensureChannel(this);
        // Repairs the alarm if it was lost (e.g. force-stop) and migrates the 1.0 alarm.
        ReminderScheduler.sync(this);

        setupToolbar();
        binding.toggleButton.setOnClickListener(v -> toggleStatus());
        binding.setReminderButton.setOnClickListener(v -> showTimePicker());
        binding.turnOffReminderButton.setOnClickListener(v -> turnOffReminder());
        binding.allowNotificationsButton.setOnClickListener(v -> requestNotificationAccess());

        // A time picker restored after rotation needs its listener re-attached.
        MaterialTimePicker restoredPicker =
                (MaterialTimePicker) getSupportFragmentManager().findFragmentByTag(TIME_PICKER_TAG);
        if (restoredPicker != null) attachPickerListener(restoredPicker);

        ads = new AdsController(this, binding.adContainer, this);
        onPrivacyOptionsAvailabilityChanged();
        ads.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Status, relative time and notification permission may have changed while away.
        renderStatus();
        renderReminder();
        ads.resume();
    }

    @Override
    protected void onPause() {
        ads.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ads.destroy();
        super.onDestroy();
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            binding.toolbar.setPadding(0, bars.top, 0, 0);
            view.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.main_menu);
        binding.toolbar.setOnMenuItemClickListener(this::onToolbarItemClicked);
    }

    private boolean onToolbarItemClicked(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_privacy_options) {
            ads.showPrivacyOptions();
            return true;
        } else if (id == R.id.action_privacy_policy) {
            openUri(Uri.parse(getString(R.string.privacy_policy_url)), R.string.error_no_browser);
            return true;
        }
        return false;
    }

    @Override
    public void onPrivacyOptionsAvailabilityChanged() {
        Menu menu = binding.toolbar.getMenu();
        MenuItem item = menu.findItem(R.id.action_privacy_options);
        if (item != null) item.setVisible(ads.isPrivacyOptionsRequired());
    }

    // ---- Door status -------------------------------------------------------------------------

    private void toggleStatus() {
        DoorState previous = store.getState();
        boolean nowLocked = !previous.locked || !previous.hasRecord;
        state = store.setLocked(nowLocked, System.currentTimeMillis());
        renderStatus();

        Snackbar snackbar = Snackbar.make(binding.getRoot(),
                nowLocked ? R.string.snackbar_marked_locked : R.string.snackbar_marked_unlocked,
                Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.action_undo, v -> {
            store.restore(previous);
            renderStatus();
        });
        if (binding.adContainer.getVisibility() == View.VISIBLE) {
            snackbar.setAnchorView(binding.adContainer);
        }
        snackbar.show();
    }

    private void renderStatus() {
        state = store.getState();
        @StringRes int title;
        @DrawableRes int icon;
        @ColorRes int container;
        @ColorRes int onContainer;
        @ColorRes int iconColor;
        if (!state.hasRecord) {
            title = R.string.status_unknown;
            icon = R.drawable.ic_status_unknown;
            container = R.color.status_unknown_container;
            onContainer = R.color.status_on_unknown_container;
            iconColor = R.color.status_unknown_icon;
        } else if (state.locked) {
            title = R.string.status_locked;
            icon = R.drawable.ic_status_locked;
            container = R.color.status_locked_container;
            onContainer = R.color.status_on_locked_container;
            iconColor = R.color.status_locked_icon;
        } else {
            title = R.string.status_unlocked;
            icon = R.drawable.ic_status_unlocked;
            container = R.color.status_unlocked_container;
            onContainer = R.color.status_on_unlocked_container;
            iconColor = R.color.status_unlocked_icon;
        }

        int containerColor = ContextCompat.getColor(this, container);
        int textColor = ContextCompat.getColor(this, onContainer);
        binding.statusCard.setCardBackgroundColor(containerColor);
        binding.statusIcon.setImageResource(icon);
        binding.statusIcon.setImageTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, iconColor)));
        int surface = MaterialColors.getColor(binding.statusIcon, com.google.android.material.R.attr.colorSurface);
        binding.statusIcon.setBackgroundTintList(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(surface, 0xB3)));
        binding.statusTitle.setText(title);
        binding.statusTitle.setTextColor(textColor);
        binding.statusChangedAt.setTextColor(textColor);
        binding.statusRelative.setTextColor(textColor);

        String changedAt;
        String relative = "";
        if (!state.hasRecord) {
            changedAt = getString(R.string.status_unknown_hint);
        } else if (state.hasChangedAtTime()) {
            changedAt = getString(R.string.status_changed_at,
                    TimeFormatting.dateTime(this, state.changedAtMillis));
            relative = TimeFormatting.relative(this, state.changedAtMillis, System.currentTimeMillis());
        } else if (state.legacyChangedAtText != null) {
            changedAt = getString(R.string.status_changed_at, state.legacyChangedAtText);
        } else {
            changedAt = getString(R.string.status_changed_at_unknown);
        }
        binding.statusChangedAt.setText(changedAt);
        binding.statusRelative.setText(relative);
        binding.statusRelative.setVisibility(relative.isEmpty() ? View.GONE : View.VISIBLE);
        binding.statusSummary.setContentDescription(getString(title) + ". " + changedAt
                + (relative.isEmpty() ? "" : ", " + relative));

        boolean nextIsLock = !state.hasRecord || !state.locked;
        binding.toggleButton.setText(nextIsLock ? R.string.action_mark_locked : R.string.action_mark_unlocked);
        binding.toggleButton.setIconResource(nextIsLock ? R.drawable.ic_button_lock : R.drawable.ic_button_unlock);
    }

    // ---- Reminder ----------------------------------------------------------------------------

    private void renderReminder() {
        ReminderTime reminder = store.getReminder();
        boolean enabled = reminder != null;
        binding.reminderSummary.setText(enabled
                ? getString(R.string.reminder_every_day_at, TimeFormatting.timeOfDay(this, reminder))
                : getString(R.string.reminder_off));
        binding.setReminderButton.setText(enabled ? R.string.reminder_change : R.string.reminder_set);
        binding.turnOffReminderButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        binding.reminderNote.setVisibility(enabled ? View.VISIBLE : View.GONE);
        binding.notificationWarning.setVisibility(
                enabled && !ReminderNotifications.canPost(this) ? View.VISIBLE : View.GONE);
    }

    private void showTimePicker() {
        ReminderTime current = store.getReminder();
        ReminderTime initial = current != null ? current : DEFAULT_REMINDER;
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(initial.hour)
                .setMinute(initial.minute)
                .setTitleText(R.string.reminder_picker_title)
                .build();
        attachPickerListener(picker);
        picker.show(getSupportFragmentManager(), TIME_PICKER_TAG);
    }

    private void attachPickerListener(@NonNull MaterialTimePicker picker) {
        picker.addOnPositiveButtonClickListener(v -> onReminderTimeChosen(
                ReminderTime.of(picker.getHour(), picker.getMinute())));
    }

    private void onReminderTimeChosen(@NonNull ReminderTime time) {
        store.setReminder(time);
        ReminderScheduler.schedule(this, time);
        renderReminder();
        showMessage(getString(R.string.reminder_saved, TimeFormatting.timeOfDay(this, time)));
        // Ask in context for the Android 13+ permission; other blocked states are explained
        // by the warning in the reminder card, which links to the system settings.
        if (isNotificationPermissionMissing()
                && (!store.wasNotificationPermissionAsked()
                || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            requestNotificationAccess();
        }
    }

    private void turnOffReminder() {
        store.clearReminder();
        ReminderScheduler.cancel(this);
        renderReminder();
        showMessage(getString(R.string.reminder_removed));
    }

    /**
     * Asks for notification permission in context (Android 13+), or opens the notification
     * settings when the permission was permanently denied or notifications were turned off.
     */
    private void requestNotificationAccess() {
        if (!isNotificationPermissionMissing()) {
            openNotificationSettings();
            return;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.notifications_rationale_title)
                    .setMessage(R.string.notifications_rationale_message)
                    .setPositiveButton(R.string.action_continue, (d, w) -> launchPermissionRequest())
                    .setNegativeButton(R.string.action_not_now, null)
                    .show();
        } else if (!store.wasNotificationPermissionAsked()) {
            launchPermissionRequest();
        } else {
            // Denied twice, or "Don't ask again": only the system settings can grant it now.
            openNotificationSettings();
        }
    }

    private boolean isNotificationPermissionMissing() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    private void launchPermissionRequest() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        store.setNotificationPermissionAsked();
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showMessage(getString(R.string.error_no_settings));
        }
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private void openUri(@NonNull Uri uri, @StringRes int errorMessage) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private void showMessage(@NonNull String message) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG);
        if (binding.adContainer.getVisibility() == View.VISIBLE) {
            snackbar.setAnchorView(binding.adContainer);
        }
        snackbar.show();
    }
}
