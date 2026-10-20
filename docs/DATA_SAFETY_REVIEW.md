# Data safety review — Door Reminder 1.2.0 (`com.dr.door_reminderapp`)

Audit date: 2026-10-13. Scope:
- release build `versionCode 4`, with production AdMob App ID `ca-app-pub-6402675413704299~9709957106` and banner unit `ca-app-pub-6402675413704299/1820407539`;
- app sources, merged release manifest, release runtime dependency tree, backup configuration, the live privacy policy, and the live Play listing.

Nothing in Play Console was changed. This is a technical review, not legal advice. You are responsible for the final answers (Google states this explicitly in the SDK disclosure).

## 1. Summary

- **The current declaration ("No data collected / No data shared") is incorrect.** The Google Mobile Ads SDK 25.5.0 bundled in the app automatically collects and shares IP address (approximate location), user product interactions, diagnostic information, and device/account identifiers. The purposes are advertising, analytics and fraud prevention.
- **The app's own code sends nothing off the device.** The door status, timestamps and reminder time stay in local SharedPreferences. There are no accounts, no own servers, and no analytics or crash reporting SDKs.
- **Android Auto Backup** can copy those preferences to the user's own Google Drive. This is done by the Android system, and on Android 9+ with a screen lock it is end-to-end encrypted. It is not recommended as a Data safety declaration; see §4.3 for the edge case.

## 2. What is in the app (evidence)

| Item | Finding | Evidence |
|---|---|---|
| App's own networking | None: no HTTP clients, sockets, WebViews, location APIs, or device-ID APIs in `app/src/main/java` | Source grep |
| Analytics / crash SDKs | None. `play-services-measurement-base` / `-sdk-api` 20.1.2 arrive transitively from the Ads SDK; these are API stubs, and no Firebase Analytics or Crashlytics is present | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` |
| Ads | `com.google.android.gms:play-services-ads-api:25.5.0`; one adaptive banner; default `AdRequest` with no non-personalized flag, no child-directed tag and no content-rating override | `AdsController.java` |
| Consent | `com.google.android.ump:user-messaging-platform:4.0.0`. `requestConsentInfoUpdate` runs on every launch, then `loadAndShowConsentFormIfRequired`. **Ads are requested only when `canRequestAds()` is true.** A "Privacy and ad settings" menu appears when UMP says privacy options are required | `AdsController.java`, `MainActivity.java` |
| Permissions in the release manifest | App: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. Merged from SDKs: `INTERNET`, `ACCESS_NETWORK_STATE`, `com.google.android.gms.permission.AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS`, `WAKE_LOCK`, `FOREGROUND_SERVICE` | `aapt2 dump badging app-release.apk` |
| No location / contacts / storage / camera / account permissions | Confirmed | same |
| Local data | `SwitchPrefs`: lock status, last-change time, reminder hour/minute, next trigger time, "notification permission asked" flag, legacy 1.x text keys. No personal identifiers | `DoorStateStore.java` |
| Backup | `allowBackup="true"`; the backup and data-extraction rule files contain only commented samples, so the defaults apply (shared preferences included) | `AndroidManifest.xml`, `res/xml/*` |

## 3. Data collected or shared by the Google Mobile Ads SDK

Source: Google Mobile Ads SDK (Legacy) — *Play data disclosure*, covering version 25.5.0, page updated 2026-10-12 (https://developers.google.com/admob/android/privacy/play-data-disclosure).

That page states:
- the SDK "automatically collects and shares" the data below, for advertising, analytics and fraud prevention;
- all of it is encrypted in transit with TLS;
- users can reset or delete the Android advertising ID in device settings.

Play definitions used for the mapping are from Play Console Help, "Provide information for Google Play's Data safety section" (https://support.google.com/googleplay/android-developer/answer/10787469):
- collection = "transmitting data from your app off a user's device", including by SDKs;
- approximate location includes locations inferred from the IP address.

| Play category → data type | SDK disclosure basis | Collected | Shared | Processed ephemerally? | Required / optional | Purposes |
|---|---|---|---|---|---|---|
| Location → **Approximate location** | IP address, "may be used to estimate the general location of a device" | Yes | Yes | No | Required | Advertising or marketing; Analytics; Fraud prevention, security, and compliance |
| App activity → **App interactions** | User product interactions (app launch, taps, video views) | Yes | Yes | No | Required | Advertising or marketing; Analytics; Fraud prevention, security, and compliance |
| App info and performance → **Diagnostics** | Diagnostic information (launch time, hang rate, energy usage) | Yes | Yes | No | Required | Advertising or marketing; Analytics; Fraud prevention, security, and compliance |
| Device or other IDs → **Device or other IDs** | Android advertising ID, app set ID, and, if applicable, identifiers of signed-in accounts on the device | Yes | Yes | No | Required | Advertising or marketing; Analytics; Fraud prevention, security, and compliance |

Notes:
- **"Required"**: users cannot turn this collection off inside the app. Ads load whenever UMP allows ad requests. Users can only reset or delete the advertising ID at the device level, and consent choices limit personalization, not all transmission.
- **"Shared"**: Google's disclosure says the SDK collects **and shares** this data. Google acts as the ad network, not only as a service provider processing on the developer's behalf, so declare it as shared.
- **Crash logs** are **not** listed in the current 25.5.0 disclosure (earlier Google guidance listed them). Recommendation: don't declare them unless you want a conservative superset (owner decision; see §6).
- **Purposes:** Google's page gives the three purposes for the SDK as a whole, not per data type. Declaring all three for each type is accurate and consistent with that page.

## 4. Data not to declare as collected

1. **Door status, timestamps, reminder time.** These are processed only on the device. Play: data "only processed locally on the user's device and not sent off device" does not need disclosure.
2. **Notification permission, alarms, boot rescheduling.** These are local OS features; nothing is transmitted.
3. **Android Auto Backup (owner awareness).**
   - Android itself uploads the app's shared preferences to a private folder in the user's own Google Drive. It is "end-to-end encrypted on devices running Android 9 or higher using the device's PIN, pattern, or password" (https://developer.android.com/identity/data/autobackup).
   - The developer cannot read it. Play excludes end-to-end-encrypted data from "collection".
   - **Edge case:** on devices without a screen lock, or on Android 7–8, the backup is not client-side encrypted.
   - If you want zero ambiguity, a future code change could restrict backup to `requireFlags="clientSideEncryption"` or exclude the prefs file. That is not done here, because it is a behavior change.
4. **Consent records (UMP).**
   - UMP stores the IAB TCF consent string and related values on the device (`IABTCF_*`, `UMP_*` keys were observed in device logs during QA). It also contacts Google to fetch the message and the consent status.
   - **Google has not published a separate Play Data safety page for UMP** (searched; not found).
   - Its traffic is to Google, alongside the Ads SDK. The device/ID and IP-based location answers in §3 already cover what such a request could carry. This is the remaining assumption (§6).

## 5. Security practices and other form questions

| Question | Recommended answer | Basis |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | §3 |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | The SDK disclosure says TLS. The app itself transmits nothing else |
| Which account creation methods does your app support? | **My app does not allow users to create an account** | No accounts in the app |
| Do you provide a way for users to request that their data is deleted? | **No** (recommended) | The developer holds no collected data and cannot delete Google's ad data. Users can reset or delete the ad ID in Android settings. Answer "Yes" only if you set up and honour a real request channel (§6) |
| Is your app's data independently validated / security reviewed? | **No** (optional badge) | No independent validation done |
| *App content → Advertising ID* declaration | **Yes, uses advertising ID** → Advertising or marketing; Analytics; Fraud prevention, security and compliance | Release manifest contains `com.google.android.gms.permission.AD_ID` |
| *App content → Ads* | **Yes, contains ads** | Banner |

## 6. Unresolved facts (owner confirmation needed)

1. **Is a GDPR (EEA/UK/Switzerland) consent message published for this app in AdMob → Privacy & messaging?**
   - The code shows the UMP form whenever AdMob says one is required, but it can only show a message that exists.
   - Google requires a Google-certified CMP that integrates with IAB TCF for personalized ads in the EEA and UK (since 16 Jan 2024) and Switzerland (since 31 Jul 2024). Without one, only Limited Ads serve there (https://support.google.com/admob/answer/13554020).
   - UMP is Google's own certified CMP, but only if the message is configured.
   - Not verifiable from here: the QA device location reported `gdprApplies=0`.
2. **US state privacy (e.g. CPRA) message.** Optional in AdMob Privacy & messaging. If you enable it, no Data safety change is needed; the same data types apply.
3. **Crash logs:** declare or not (§3 note). The current SDK disclosure does not list them.
4. **Deletion requests:** keep "No", or commit to handling email requests at superadmirapps@gmail.com. Note that you would have no data of your own to delete.
5. **Target audience:** confirm in Play Console that the app is **not** aimed at children. Ads are not configured for child-directed treatment, and the Families policy would change the answers above.
6. **UMP traffic:** assumed covered by §3, since Google publishes no separate UMP Play disclosure.

## 7. Privacy policy (live) — problems and corrections

URL: https://superadmir.blogspot.com/2025/07/privacy-policy-for-door-reminder-app.html (read on 2026-10-13).

| Issue in the current policy | Correction |
|---|---|
| States information "will be retained on your device and is not collected by me in any way". | True for the developer, but the Ads SDK collects data. Say that the developer does not collect it, and **Google AdMob does**, as described below. |
| No mention of the **advertising ID**, IP-based approximate location, app interactions or diagnostics. | List them, with purposes (advertising, analytics, fraud prevention) and sharing with Google. |
| No mention of **consent** (UMP) or the in-app **"Privacy and ad settings"** option. | Describe the consent message for EEA/UK/Swiss users and how to change choices later. |
| No deletion or control information beyond uninstalling. | Explain: uninstall or clear app data to remove local data; reset or delete the advertising ID in Android settings; link to Google's policy. |
| **Effective date "2030-01-31"** (in the future). | Use the real publication date of the revised policy. |
| Does not mention Android backup. | One sentence: Android may include the app's settings in the user's own encrypted Google backup; the developer has no access to it. |

Suggested replacement paragraph (adapt as needed):

> Door Reminder stores your door status and reminder time only on your device; the developer does not collect or receive this information. If Android backup is enabled, Android may include these settings in your own Google account backup, which the developer cannot access.
> The app shows ads from Google AdMob. The Google Mobile Ads SDK collects and shares with Google your device's advertising ID and app set ID, your IP address (which may be used to estimate approximate location), interactions with the app and ads, and diagnostic information. Google uses this for advertising, analytics and fraud prevention. The data is encrypted in transit. Users in the EEA, UK and Switzerland are asked for consent, and anyone can change their choices under "Privacy and ad settings" in the app menu when it is offered.
> You can reset or delete your advertising ID in Android Settings → Privacy → Ads. Uninstalling the app deletes all data it stored on your device. Learn how Google uses this information: https://policies.google.com/technologies/partner-sites.
> Contact: superadmirapps@gmail.com. Effective date: <date you publish this revision>.

## 8. Play Store listing consistency

- **Live listing:** "No login or internet required" is inaccurate (ads use the internet), and the live Data safety section is incorrect (this review).
- **Proposed 1.2.0 listing** (`docs/PLAY_STORE_LISTING.md`): consistent with these findings. It says "No account needed, and the main features work offline" and "The app is free and supported by ads". The earlier "stored only on your phone" claim was already removed.

## 9. Exact Play Console answers (enter after confirming §6)

**Policy → App content → Data safety → Start / Edit:**

1. *Data collection and security*
   - Does your app collect or share any of the required user data types? → **Yes**
   - Is all of the user data collected by your app encrypted in transit? → **Yes**
   - Which of the following methods of account creation does your app support? → **My app does not allow users to create an account**
   - Do you provide a way for users to request that their data is deleted? → **No** (see §6.4)
2. *Data types*: tick exactly:
   - Location → **Approximate location**
   - App activity → **App interactions**
   - App info and performance → **Diagnostics** (and **Crash logs** only if you choose the conservative option, §6.3)
   - Device or other IDs → **Device or other IDs**
3. *Data usage and handling*: for **each** ticked type:
   - Is this data collected, shared, or both? → **Collected** ✔ and **Shared** ✔
   - Is this data processed ephemerally? → **No**
   - Is this data required for your app, or can users choose whether it's collected? → **Data collection is required (users can't turn off this data collection)**
   - Why is this user data collected? → **Advertising or marketing**, **Analytics**, **Fraud prevention, security, and compliance**
   - Why is this user data shared? → **Advertising or marketing**, **Analytics**, **Fraud prevention, security, and compliance**
4. Preview: the summary should show *Data shared: Location, App activity, App info and performance, Device or other IDs*; *Data collected: same*; *Data is encrypted in transit*. Then submit.
5. Also check **App content → Advertising ID**: *Yes* → Advertising or marketing, Analytics, Fraud prevention, security and compliance.
6. Update the **privacy policy** (§7) **before or together with** the 1.2.0 release, and keep the same URL in Store settings.
