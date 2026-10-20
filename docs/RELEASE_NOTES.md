# Release notes — Door Reminder 1.2.0 (versionCode 4)

## Play Console "What's new" (en-US, 468 / 500 characters)

```
• Fresh new look with light and dark themes
• One clear button to mark your door as locked or unlocked, with Undo
• Daily reminders are more reliable: they keep working after restarts, time-zone changes and daylight-saving time
• A reminder set for a time that already passed today no longer fires immediately
• Dates and times follow your phone's language and 12/24-hour setting
• Clear notice if notifications are turned off
• New privacy and ad settings in the menu
```

## Technical changelog (for maintainers)

- Targets Android 16 (API 36); compileSdk 36; AGP 9.4.1, Gradle 9.7.1, Java 17.
- applicationId set to `com.dr.door_reminderapp` (Play listing); versionCode 3 → 4.
- Reminder scheduling rewritten: one-shot alarms rescheduled every day for the local wall-clock time; reschedule on boot / package update / time / time-zone change.
- Existing 1.x data (status, last-change text, reminder text) migrated in place; same preference file
  and notification channel ID.
- UMP consent flow per Google guidance; privacy options entry point; Mobile Ads initialized once on a
  background thread; adaptive anchored banner below content.
- Release: R8 + resource shrinking, `mapping.txt` generated (upload it with the bundle).
- Ad IDs injected per build type; release build refuses missing/test IDs.
- Reminder uses a 10-minute windowed alarm plus a Doze-tolerant backup (no exact-alarm permission).
- WorkManager pinned to 2.11.2 (fixes an R8 release-only startup crash from the Ads SDK's WorkManager 2.7.0).
- 48 JVM/Robolectric unit tests and 4 instrumented tests (run on a Samsung SM-S711B, Android 16).
