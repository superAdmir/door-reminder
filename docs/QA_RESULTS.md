# QA Results — Door Reminder 1.2.0

Date: 2026-10-13. Host: macOS (Intel i7), JDK Temurin 17.0.13, Android SDK build-tools 36.0.0.
Device: Samsung **SM-S711B, Android 16 (API 36)**, connected over USB with the owner's authorization.
Every result below was observed directly. Anything not listed was not run.

## Baseline (unchanged commit `e90a0b9`, Gradle 8.7 / AGP 8.6.1)

| Command | Result |
|---|---|
| `./gradlew assembleDebug testDebugUnitTest` | BUILD SUCCESSFUL; 1/1 test passed (placeholder) |
| `./gradlew lintDebug` | 0 errors, 68 warnings |

## Final run (Gradle 9.7.1 / AGP 9.4.1, after `./gradlew clean`)

| Command | Result |
|---|---|
| `./gradlew testDebugUnitTest` | **48 tests, 0 failures** |
| `./gradlew lintDebug` | **0 errors, 32 warnings** (see below) |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew connectedDebugAndroidTest` (SM-S711B) | **4 tests, 0 failures** |
| `./gradlew bundleRelease assembleRelease -PdoorReminder.allowTestAdsInRelease=true` | BUILD SUCCESSFUL (QA build with Google **test** ads; not for upload) |
| `./gradlew bundleRelease` (no ad IDs) | **Fails by design:** "Release builds need production AdMob IDs…" |
| `./gradlew bundleRelease` with test IDs passed as production IDs | **Fails by design:** "Google test AdMob IDs must not be used as production IDs." |

### Unit tests (JVM plus Robolectric, run against SDK 34)

- `ReminderTimeTest` (17): validation; next trigger later today, already passed (the 1.x "fires immediately" bug), exactly now, month/year rollover, DST spring-forward/fall-back (23 h / 25 h gaps, local time kept), time inside the DST gap, time-zone dependence, 400-day hourly sweep; legacy `"hh:mm a"` parsing, including a round-trip in **every JVM locale**.
- `DoorStateStoreTest` (13): first launch; atomic status and timestamp; undo/restore; upgrade from 1.x (US and Swedish data); `--:--` placeholder; unparseable legacy values; corrupt types; out-of-range values; negative timestamp.
- `ReminderSchedulingTest` (17): windowed plus Doze-backup alarms at the correct time; replacement, not duplication; the 1.x repeating alarm is replaced or cancelled; migration; cancel; immutable explicit `PendingIntent`; receiver posts the notification (channel, auto-cancel, private visibility, public version) and reschedules; permission denied; channel blocked; reminder turned off; **twin alarm does not notify twice**; boot / package-replaced / time / time-zone reschedule; spoofed or null intents ignored.
- `ExampleUnitTest` (1): original placeholder, kept.

### Instrumented tests on the SM-S711B (Android 16)

`useAppContext`, `scheduleAndCancel_registerAndRemoveTheAlarm`, `receiver_postsVisibleNotificationAndReschedules`, `mainActivity_togglePersistsAndSurvivesRecreation`: all passed.

### Manual / scripted checks on the device (adb, screenshots reviewed)

| Check | Observed |
|---|---|
| First launch (dark mode) | "No status recorded yet"; edge-to-edge correct; no overlap |
| Consent / ads (debug, test IDs) | UMP request OK (`gdprApplies=0` at the device's location); test adaptive banner loaded **below** the content |
| Mark as locked | Green card, "Updated Tue 13 Oct, 10:24" (en-GB, 24 h), Snackbar with Undo above the banner; prefs `isLocked=true`, `last_changed_at` set |
| Set reminder | Material time picker in 24 h (matches the device setting); the **notification permission prompt appeared in context** after confirming |
| Alarm registration | `dumpsys alarm`: RTC_WAKEUP at the chosen time, `repeatInterval=0` |
| **Real notification delivery** | Trigger 10:27:00, notification present by the 10:27:37 poll. With the final two-alarm build: trigger 10:33:00, posted 10:34:53; one notification; title "Did you lock the door?", `vis=PRIVATE`, `AUTO_CANCEL`, channel `door_lock_reminder` |
| Next-day reschedule | Both alarms moved to 2026-10-14 (windows +10 min and +1 h) |
| **App update reschedule** | After `installDebug` over existing data, without opening the app: both alarms re-registered (`MY_PACKAGE_REPLACED`) |
| Light theme plus 1.3× font | Readable; content scrolls; banner never covers controls. The device's dark mode and font scale 1.0 were restored |
| Turn off | 0 alarms remain; reminder prefs removed |
| **R8 release build** (debug-signed copy in the scratchpad) | **First attempt crashed at launch** (WorkManager/Room stripped by R8). After the fix: launch OK (777 ms), toggle works, "Just now", **0 crashes**. QA build uninstalled afterwards |

### Remaining lint warnings (32, 0 errors)

- 16 × `UnusedResources` and 3 × `IconLocation`: legacy files kept because of the no-deletion rule; stripped from release by `shrinkResources`.
- 5 × `IconDuplicates` (`ic_launcher` = `ic_launcher_round`, original assets).
- 1 × `ObsoleteSdkInt` (`values-v23`).
- 1 × `OldTargetApi` (API 37 exists; Play requires 36).
- 6 × newer versions available (4 `GradleDependency`, 1 Gradle, 1 Robolectric): these need compileSdk 37 (`core` 1.19 was tried and refused by AAR metadata) or are not essential.

## Limitations / not verified

- **Robolectric runs SDK 34, not 36.** Robolectric needs Java 21 for SDK 35+, and only JDK 17 is installed. Behaviour on 36 was covered by the device runs instead.
- **Emulator unusable.** The API 35 AVD never finished booting on this Intel Mac (SurfaceFlinger did not start). All runtime checks used the real phone.
- **Not tested on a device:**
  - reboot (`BOOT_COMPLETED`; covered by unit tests only; the phone was not rebooted)
  - manual time-zone change
  - deep Doze / overnight delivery
  - Android < 13 devices
  - tablets and foldables
  - TalkBack navigation
  - rotation while the time picker is open
  - upgrade from the **real Play 1.1.0** build with real user data (the Play build was removed from the phone before it could be inspected)
- Production AdMob/UMP behaviour (real ads, an EEA consent form) can only be verified with production IDs.
- Release signing was not exercised: no upload key is available.

## Final re-run after the legacy icon fix (2026-10-13)

| Command | Result |
|---|---|
| `./gradlew clean` then `testDebugUnitTest lintDebug assembleDebug` | BUILD SUCCESSFUL; **48 tests, 0 failures**; lint **0 errors, 27 warnings** |
| `./gradlew bundleRelease assembleRelease -PdoorReminder.allowTestAdsInRelease=true` | BUILD SUCCESSFUL (QA build) |
| `./gradlew bundleRelease` (no production IDs) | Fails by design (release guard) |
| Legacy icon checks (10 files) | Correct sizes 48–192 px; corner alpha 0, centre alpha 255 at every density |
| Play artwork (7 PNGs) | PNG signature valid; exact dimensions; no transparency; sizes 35 KB – 428 KB (well under the limits) |

Lint dropped from 32 to 27 warnings: the 5 `IconDuplicates` warnings are gone, because `ic_launcher` and `ic_launcher_round` were identical square tiles before the fix. One new `UnusedResources` warning appeared: `R.color.launcher_background` is now unused, since the adaptive icon uses the gradient drawable. Device tests were not repeated, by request.
