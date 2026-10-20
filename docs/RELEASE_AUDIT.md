# Door Reminder — Release Audit

Audit date: 2026-10-13 · Audited commit: `e90a0b9` (branch `main`, in sync with `origin/main`)

## 1. Baseline (before changes)

| Item | Repository (`e90a0b9`) | Google Play (verified) |
|---|---|---|
| applicationId | `com.example.door_reminderapp` | `com.dr.door_reminderapp` |
| namespace / Java package | `com.example.door_reminderapp` | unknown (APK could not be inspected) |
| versionCode / versionName | 1 / 1.0 | **3 / 1.1.0** (read from a Play-installed phone) |
| minSdk / targetSdk / compileSdk | 24 / 34 / 34 | – / 34 / – |
| Language | Java 8 source level, no Kotlin | – |
| AGP / Gradle | 8.6.1 / 8.7 | – |
| ABIs | No native code, so all ABIs are supported | – |
| Signing | No `signingConfig`, no keystore in the repo or on this machine for this app | Play App Signing (assumed) |
| AdMob App ID / banner unit | **Google test IDs** | Production IDs (not in the repo) |
| UMP | `2.1.0` declared, but overridden transitively by the Ads SDK | – |
| Last Play update | – | Aug 1, 2025 · 50+ downloads · "Contains ads" |

Working tree at start: clean except for the untracked `.gradle/` folder. It has been preserved.

Baseline checks (run before any change): `assembleDebug` OK; `testDebugUnitTest` 1/1 passed (placeholder test only); `lintDebug` 0 errors and 68 warnings.

**Conclusion:** the repository was not the source of the published build. It could not have produced an update to the Play listing: the package ID was wrong, versionCode was lower than Play's, ad IDs were test IDs, and targetSdk was too old.

## 2. Findings and fixes

Severity: **B** = release blocker, **H** = high, **M** = medium, **L** = low.

| # | Sev | Finding | Status |
|---|---|---|---|
| B1 | B | `applicationId` was `com.example.door_reminderapp`. Play rejects `com.example.*` packages, and it does not match the listing. | **Fixed.** Set to `com.dr.door_reminderapp`. The Java namespace is unchanged. |
| B2 | B | `versionCode 1` is lower than Play's versionCode 3. | **Fixed.** Now `versionCode 4`, `versionName 1.2.0`. |
| B3 | B | Production manifest and layout contained Google **test** AdMob IDs. The production IDs are not in the repo. | **Mitigated.** IDs are now injected per build type. Release builds fail when production IDs are missing or are test IDs. **The owner must supply the IDs.** |
| B4 | B | targetSdk 34. Since Aug 31, 2026, Play requires updates to target API 36. | **Fixed.** compileSdk and targetSdk are now 36. |
| B5 | B | No upload-key signing configuration. No Door Reminder keystore found on this machine. | **Open (owner).** Optional `keystore.properties` hook added. No key was generated. |
| B6 | B | The Play Data safety form says "No data collected / shared". The app ships the Google Mobile Ads SDK, which collects device identifiers (Advertising ID, `AD_ID` permission), diagnostics and approximate location for ads. | **Open (owner, Play Console).** |
| H1 | H | `setRepeating()` with a time earlier than now fired the reminder **immediately** when it was set. | **Fixed.** The next occurrence is computed strictly in the future. |
| H2 | H | Reminders were lost after a device reboot (no boot receiver). | **Fixed.** `RescheduleReceiver` handles boot, app update, clock changes and time-zone changes. |
| H3 | H | A fixed 24 h repeat drifts one hour after DST changes. Time-zone changes were not handled. | **Fixed.** Each alarm is rescheduled for the local wall-clock time. Covered by tests. |
| H4 | H | The reminder was stored only as a display string (`"09:30 PM"`), so it could not be rescheduled. | **Fixed.** Hour and minute are now stored. 1.0/1.1 data is migrated on first read (tested across all JVM locales). |
| H5 | H | The status popup was dismissed by a `Handler` 4 s later, even if the Activity was gone (window-leak crash risk). It also appeared on every launch and rotation. | **Fixed.** The popup is removed and replaced by a Snackbar with Undo. |
| H6 | H | Consent: ads were initialized after consent **errors** without checking `canRequestAds()`. There was no privacy-options entry point, and `MobileAds.initialize` ran on the main thread on every `onCreate`. | **Fixed.** The flow follows Google's UMP pattern. "Privacy and ad settings" menu entry added (shown when required). SDK init runs once per process on a background thread. |
| H7 | H | targetSdk 35+ forces edge-to-edge, so content would draw under the system bars. | **Fixed.** `EdgeToEdge` plus inset handling. |
| H8 | H | **Found during on-device QA:** after enabling R8, the release build crashed at launch (`Failed to create an instance of androidx.work.impl.WorkDatabase`). WorkManager 2.7.0 comes in through the Ads SDK, and R8 full mode (AGP 9 default) stripped its Room database. | **Fixed.** WorkManager pinned to 2.11.2 plus a Room keep rule. The release build was re-verified on the device: launches, 0 crashes. |
| H9 | H | **Found during on-device QA:** an inexact alarm scheduled a day ahead got a **1-hour** delivery window on Android 16 (`dumpsys alarm`). | **Fixed.** A second `setWindow` alarm with a 10-minute window is scheduled for the same moment. The Doze-tolerant alarm remains as a backup, and duplicate delivery is suppressed. Still no exact-alarm permission. |
| M1 | M | Notification permission was requested on launch with no context and no handling for denial. | **Fixed.** It is requested when a reminder is set. A rationale dialog appears when appropriate. A warning card links to settings when notifications are blocked. |
| M2 | M | The notification had no tap action and was not auto-cancelled. Strings were hard-coded. The channel was created only when the alarm fired. | **Fixed.** Tapping opens the app, the channel is created at start-up, and the door status is hidden on the lock screen (`VISIBILITY_PRIVATE`). |
| M3 | M | Hard-coded strings and colours. Times were always 12 h and English-formatted. The time picker was forced to 12 h. | **Fixed.** All text is in resources. Times follow device locale, time zone and 12/24 h setting. |
| M4 | M | The banner was anchored independently of the content and could overlap buttons on small screens, in landscape, or with large fonts. | **Fixed.** Content scrolls, and the banner sits below it in its own container. |
| M5 | M | Unused `navigation-*` dependencies. UMP declared at 2.1.0 but resolved to a different version. | **Fixed.** Navigation removed. UMP aligned to 4.0.0 (the version the Ads SDK requires). |
| M6 | M | No `.gitignore`, so keystores and `local.properties` could be committed. | **Fixed.** |
| M7 | M | `AdView` was never paused, resumed or destroyed. | **Fixed.** |
| M8 | M | App wording implied a real lock state ("Door is LOCKED"). | **Fixed.** Now "Marked as locked", plus an explicit disclaimer. |
| L1 | L | Unused resources (`splash.png` 656 KB, `lock_*.png`, `dialog_popup.xml`, etc.). | Left in place (no-delete rule). Removed from the release build by `shrinkResources`. Safe for the owner to delete. |
| L2 | L | The adaptive icon used the Android Studio template background and had no themed (monochrome) layer. | **Fixed.** Solid background plus monochrome layer. Foreground artwork unchanged. |
| L3 | L | R8 disabled in release. | **Fixed.** Minify and resource shrinking on. `mapping.txt` is produced. |
| L4 | L | `google-services.json` is an empty stub and no Firebase plugin is applied. | Left untouched; it has no effect. |

## 3. Inferred risks (not verifiable here)

1. **Published code is unknown.** The Play APK could not be inspected (the Play build was removed from the test phone during the session). Migration assumes the published 1.1.0 uses the same preference file and keys as this repo (`SwitchPrefs`, `isLocked`, `last_checked_time`, `reminder_time`) and the same receiver class (`com.example.door_reminderapp.ReminderReceiver`).
   - If it does not, existing users will see "No status recorded yet" once and must set their reminder again.
   - If the launcher activity class name differs, some launchers' home-screen shortcuts may need re-adding.
2. **Inherited permission.** `FOREGROUND_SERVICE` is merged from WorkManager 2.7.0, which comes in through the Ads SDK. No typed foreground-service permission is declared, so the Play foreground-service declaration should not apply. Confirm in Play Console pre-launch checks.
3. **Reminder timing.** No exact-alarm permission is used. While the phone is not in deep idle, delivery is within about 10 minutes. In deep Doze the backup alarm may arrive up to about 1 hour late. Aggressive OEM battery managers can delay further. The UI says reminders may be a few minutes late.

## 4. Architecture after changes

The single-Activity Java app is kept; there was no framework migration. Logic was extracted into small, testable classes:

- `DoorStateStore`: persistence, validation, migration of 1.0/1.1 data
- `ReminderTime`: next-trigger computation (DST-safe), legacy parsing
- `ReminderScheduler`: two alarms per occurrence (10-minute window plus Doze backup); keeps the 1.x `PendingIntent` identity so the old repeating alarm is replaced, not duplicated
- `ReminderReceiver` / `RescheduleReceiver`: alarm firing; boot, update and clock changes
- `ReminderNotifications`: channel, permission checks, notification
- `AdsController`: UMP consent, then one-time SDK init, then adaptive anchored banner
- `MainActivity`: UI only
