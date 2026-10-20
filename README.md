# Door Reminder

Android app (Java) to record whether you marked your door as locked or unlocked, with a daily reminder.
Google Play: https://play.google.com/store/apps/details?id=com.dr.door_reminderapp

The app records what the user enters. It does not detect a real lock.

## Build

Requirements: JDK 17 and the Android SDK (platform 36).

```bash
./gradlew assembleDebug          # debug build (com.dr.door_reminderapp.debug, Google test ads)
./gradlew testDebugUnitTest      # JVM + Robolectric tests
./gradlew lintDebug
./gradlew connectedDebugAndroidTest   # needs a device or emulator
```

Release builds need production AdMob IDs (Gradle properties `doorReminder.admobAppId` and `doorReminder.admobBannerUnitId`) and, for signing, a git-ignored `keystore.properties`. See [docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md).

## Docs

- [docs/RELEASE_AUDIT.md](docs/RELEASE_AUDIT.md): baseline, findings, fixes
- [docs/QA_RESULTS.md](docs/QA_RESULTS.md): what was tested and how
- [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md): "What's new" text
- [docs/PLAY_STORE_LISTING.md](docs/PLAY_STORE_LISTING.md): listing proposal and asset checklist
- [docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md): blockers and release steps
