# Release readiness — Door Reminder 1.2.0 (versionCode 4)

Status as of 2026-10-15.

**Update 2026-10-20: 1.2.0 (versionCode 4) is published to Google Play Production** (owner confirmed). The release AAB, mapping file and a release record are backed up outside the repository in `~/Documents/door-reminder-release-backup/1.2.0-versionCode4/`. Post-release checks still apply: watch Android vitals, and verify the upgrade from 1.1.0, reminders after a reboot, and the consent message in the EEA.

**Pre-release recommendation (2026-10-15): Conditional GO.** The production AAB is built, signed with the approved upload key, and verified (§4). Upload it only after the Data safety form and privacy policy (B3) are corrected in Play Console. Direct-to-production was chosen by the owner, so B4 (upgrade from the real 1.1.0) is untested; a staged rollout is strongly recommended.

## 1. Completed and verified

| Area | Result | How verified |
|---|---|---|
| App identity | `com.dr.door_reminderapp`, versionCode 4 / versionName 1.2.0 (Play has 3 / 1.1.0) | Play-installed build read on the device |
| Target API | targetSdk 36 / compileSdk 36; minSdk 24 unchanged | `aapt2 dump badging` |
| Unit tests | **48 / 48 passed** | `./gradlew clean testDebugUnitTest` (after the final icon fix) |
| Lint | **0 errors, 27 warnings** (16 legacy unused resources, 3 icon-location, 6 newer-version notices, 1 obsolete-SDK folder, 1 OldTargetApi) | `./gradlew lintDebug` |
| Debug build | BUILD SUCCESSFUL | `./gradlew assembleDebug` |
| Release build (R8) | `bundleRelease` + `assembleRelease` BUILD SUCCESSFUL. QA build only: Google test ad IDs, unsigned | `-PdoorReminder.allowTestAdsInRelease=true` |
| Release guard | `bundleRelease` without production IDs **fails as designed** | Re-run after the icon fix |
| Device tests | 4/4 instrumented tests. Real notification delivery, reschedule after app update, permission flow, themes, 1.3× font, R8 startup (crash found and fixed) | Samsung SM-S711B, Android 16 (earlier session; not repeated) |
| Launcher icon | Adaptive (API 26+) verified on the device. Legacy API 24–25 rasters fixed: rounded square / circle, transparent outside the shape, at all 5 densities | Pixel alpha checks plus rebuild |
| Store artwork | Icon, feature graphic and 5 real screenshots ready | See `artwork/play-store/README.md` |
| Store text | Proposed listing checked claim-by-claim against the app | `docs/PLAY_STORE_LISTING.md` |
| Production AdMob IDs | Release APK/AAB contain the production App ID and banner unit; no test IDs; debug keeps test IDs | `aapt2 dump`, byte search of the release outputs |
| New upload key | Created outside the repo; release signing config resolves to it (SHA-256 `5A:37:…:1E:BF`). Not usable until Google approves the reset (B2) | `keytool` verification, `./gradlew :app:signingReport` |

## 2. Release blockers (owner action required)

- **B1. Production AdMob IDs: ✅ configured 2026-10-13.**
  - They live in `~/.gradle/gradle.properties` (outside the repo; created new, no other properties in that file):
    - `doorReminder.admobAppId=ca-app-pub-6402675413704299~9709957106`
    - `doorReminder.admobBannerUnitId=ca-app-pub-6402675413704299/1820407539`
  - Verified in the built artifacts:
    - The release APK and AAB contain exactly these IDs (manifest `APPLICATION_ID` and `admob_banner_unit_id`).
    - The release outputs contain **0** occurrences of Google's test publisher ID.
    - The debug build still uses Google test IDs (`com.dr.door_reminderapp.debug`).
  - Not verified: live ad serving and the EEA consent form with these IDs (manual test M4). Don't tap your own ads while testing.
- **B2. Upload key: ✅ Google approved the upload-key reset (owner, 2026-10-15).** The approved certificate SHA-256 `5A:37:…:1E:BF` matches the local keystore (verified 2026-10-15).
  - The original upload key was not found on this Mac (read-only search, 2026-10-13). Play Console's registered (old) upload certificate is SHA-256 `E1:4E:23:0C:C2:8B:D7:99:98:B8:30:36:CC:97:36:51:A4:44:16:33:F8:11:CC:25:A5:20:58:BF:E2:56:70:E9`.
  - New upload key (Door Reminder only):

    | Item | Value |
    |---|---|
    | Keystore | `/Users/AdmirSatara/Documents/door-reminder-upload-key/door-reminder-upload.p12` (PKCS12, file mode 600) |
    | Alias | `door-reminder-upload` |
    | Key | RSA 4096, SHA256withRSA, valid 2026-10-13 → 2056-10-05 |
    | Public certificate (for Google) | `/Users/AdmirSatara/Documents/door-reminder-upload-key/door-reminder-upload-certificate.pem` |
    | **New SHA-256** | `5A:37:28:1C:B6:D8:3D:CB:40:1E:AF:7E:1F:29:10:A5:35:1F:93:BA:9A:44:9E:E6:3F:04:C0:80:0B:EC:1E:BF` (differs from the old one) |
    | Password | Random, 40 characters; the same for store and key (PKCS12). Kept in the macOS login Keychain, item **"Door Reminder upload keystore"**, and in the git-ignored `keystore.properties` (mode 600). It was never printed. |

  - Signing config: `keystore.properties` in the project root points to the keystore. `./gradlew :app:signingReport` shows the release variant using alias `door-reminder-upload` with the new SHA-256.
  - **Owner action: request the reset.** In Play Console → Door Reminder → **Test and release → App integrity → App signing → "Request upload key reset"**:
    1. Choose the reason "I lost my upload key".
    2. Upload `door-reminder-upload-certificate.pem`.
    3. Submit.
    - Google emails you when it is approved, usually after a waiting period of a few days. The new key works only after the date in that email.
    - **Until then, do not upload any AAB.** Uploads signed with the new key are rejected.
  - **Back up now (owner):** store the keystore (`.p12`) and its password **separately**, ideally in two places.
    - A password manager entry holding the password, with the `.p12` attached or in an encrypted vault.
    - An encrypted USB drive or an encrypted disk image kept offline.
    - Read the password locally with: `security find-generic-password -s "Door Reminder upload keystore" -w`. Run it in your own Terminal; don't paste it into chats or tickets.
    - Do not email the key or commit it. Losing it again means another reset.
  - iCloud: it could not be determined whether `~/Documents` syncs to iCloud ("Desktop & Documents Folders"). macOS privacy protection blocked the check. If it is enabled, the key folder syncs too. Check System Settings → Apple Account → iCloud → iCloud Drive.
- **B3. Data safety form: audit done 2026-10-13; ❌ still open until you apply the answers in Play Console.**
  - The live form ("No data collected / shared") is **incorrect**.
  - `docs/DATA_SAFETY_REVIEW.md` §9 has the exact answers: Approximate location, App interactions, Diagnostics, and Device or other IDs; each collected **and** shared, required, for advertising, analytics and fraud prevention; encrypted in transit; no accounts; no deletion mechanism. They are based on Google Mobile Ads SDK 25.5.0's official disclosure.
  - Confirm first (review §6):
    - a GDPR message is published in AdMob → Privacy & messaging;
    - your choice on crash logs and on deletion requests;
    - the target audience is not children.
  - **Privacy policy must be revised.** It omits the advertising ID, consent and Google AdMob data collection, and shows effective date "2030-01-31". Corrections and replacement text are in review §7.
  - Mark B3 done only after the form is submitted and the public listing's Data safety section shows the new answers.
- **B4. Published-code mismatch.** The live 1.1.0 was not built from this repository.
  - If its source exists elsewhere, check that it used the same prefs file and keys (`SwitchPrefs`: `isLocked`, `last_checked_time`, `reminder_time`) and the same class names.
  - Otherwise, accept this risk: existing users may see "No status recorded yet" once and need to set their reminder again (no crash). Test M1 below will show which case applies.

## 3. Outstanding manual actions (Play Console, no code changes)

1. Upload `artwork/play-store/icon-512.png`, `feature-graphic.png` and `screenshot-01…05-*.png`.
2. Replace the short and full description with the text in `docs/PLAY_STORE_LISTING.md`.
3. Paste the "What's new" text from `docs/RELEASE_NOTES.md`.
4. Upload `app/build/outputs/mapping/release/mapping.txt` with the signed AAB, for de-obfuscated crash reports.
5. Confirm target audience (not children) and that no foreground-service declaration is requested. `FOREGROUND_SERVICE` comes from WorkManager, without a typed FGS permission.

## 4. Production AAB (built 2026-10-15)

| Item | Verified value |
|---|---|
| Path | `/Users/AdmirSatara/door-reminder/app/build/outputs/bundle/release/app-release.aab` |
| Size / SHA-256 | 3,265,773 bytes / `449e10dbe189c7d6f5fbe76deb822627707d56cbe86819de59f6df3189a7ba68` |
| Signer | CN=Admir Satara, OU=superadmir… · SHA-256 `5A:37:28:1C:B6:D8:3D:CB:40:1E:AF:7E:1F:29:10:A5:35:1F:93:BA:9A:44:9E:E6:3F:04:C0:80:0B:EC:1E:BF` (= approved upload cert); `jarsigner -verify`: jar verified (self-signed, as normal for upload keys) |
| Package / version | `com.dr.door_reminderapp`, versionCode 4, versionName 1.2.0, minSdk 24, targetSdk 36, compileSdk 36, not debuggable |
| AdMob | App ID `ca-app-pub-6402675413704299~9709957106`, banner `ca-app-pub-6402675413704299/1820407539`; **0** occurrences of Google's test publisher ID in the whole AAB |
| Obfuscation map | Included in the bundle (`BUNDLE-METADATA/.../proguard.map`); also at `app/build/outputs/mapping/release/mapping.txt` |
| Checks | `./gradlew testDebugUnitTest bundleRelease --rerun-tasks`: 48/48 unit tests; `verifyReleaseAdConfig`, `validateSigningRelease`, `lintVitalRelease`, `signReleaseBundle` all passed |

To rebuild: `./gradlew clean testDebugUnitTest lintDebug bundleRelease` (JDK 17). Never use the QA flags `-PdoorReminder.allowTestAdsInRelease` / `-PdoorReminder.screenshotMode` for an upload build.

## 5. Remaining manual tests

| # | Test | Why |
|---|---|---|
| M1 | Install the internal-testing build **over the real Play 1.1.0** that has saved status and a reminder | Data migration from the unknown published code (B4) |
| M2 | Reboot with a reminder set; confirm the next reminder arrives | Boot reschedule is unit-tested only; the store text relies on it |
| M3 | Overnight / deep-Doze delivery | Expected ≤ ~1 h worst case; not observed |
| M4 | EEA consent form and "Privacy and ad settings" with production IDs (e.g. VPN to an EEA country) | Only test IDs were used; consent did not apply at the test location |
| M5 | TalkBack pass; rotation with the time picker open; a tablet or foldable | Not tested |
| M6 | Android 7–12 device (legacy icon, no runtime notification permission) | Only Android 16 was tested |

## 6. Unverified risks

- **Data migration** depends on the unknown published code (B4).
- **Reminder timing** with no exact-alarm permission: typically within about 10 minutes; up to about 1 h in deep Doze; aggressive OEM battery managers can delay further.
- **Real ads and the EEA consent form** have not been exercised (no production IDs).
- **Robolectric tests run on SDK 34** (JDK 17 limits them); API 36 behaviour was covered on the phone.
- **Data in backups:** Android Auto Backup is on, so the door status can be restored onto a new phone. That is harmless, but it is why the listing does not say "stored only on your phone".
