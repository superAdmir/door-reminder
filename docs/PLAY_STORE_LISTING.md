# Play Store listing — review and proposal

Current listing captured 2026-10-13 from
https://play.google.com/store/apps/details?id=com.dr.door_reminderapp. Nothing in Play Console was changed.

## Issues in the current (live) listing

| Current text / setting | Problem |
|---|---|
| Title "Door Reminder", but the description starts "Did I Lock the Door? helps…" | The description uses a different name than the title. |
| "No login or internet required" | Core features work offline, but the app shows ads, which use the internet. |
| "you'll always know" / "never forget" | Overclaims: the app records what the user enters and cannot guarantee notification delivery. |
| Data safety: "No data collected", "No data shared with third parties" | **Inconsistent with the bundled Google Mobile Ads SDK** (device/advertising IDs, app interactions, diagnostics, approximate location for ads). Must be corrected before the next release. |
| Typos: "time:view", "fast- No" | Formatting. |
| Screenshots | Show the 1.0/1.1 interface; replace them with the 1.2.0 set below. |

## Proposed text

Every claim was checked against the 1.2.0 code and UI. The verification table follows the text.

**App name (≤ 30):** `Door Reminder` (current; recommended). Alternative: `Door Reminder: Did I Lock It?` (29 characters).

**Short description (≤ 80):**
`Mark your door locked or unlocked in one tap and get a daily reminder to check.` (79 characters)

**Full description:**

```
Left home and can't remember if you locked the door? Door Reminder gives you a quick, simple record.

• One tap: mark your door as locked or unlocked, with Undo if you tap by mistake
• See when you last updated the status
• Daily reminder at the time you choose, a nudge to check your door
• Reminders are rescheduled automatically after a restart, a time-zone change or a daylight-saving change
• A clear notice if notifications are turned off, so you know when a reminder can't appear
• Light and dark themes with large, clear buttons
• No account needed, and the main features work offline

Door Reminder records the status you enter. It does not connect to smart locks and cannot detect whether your door is actually locked. Android may deliver reminders a few minutes late to save battery.

The app is free and supported by ads.
```

### Claim verification

| Claim | Evidence | Status |
|---|---|---|
| One tap; Undo | Device run; screenshots 01/02 | ✅ Verified on device |
| When you last updated | Status card ("Updated Tue 13 Oct, 10:58") | ✅ Verified on device |
| Daily reminder at a chosen time | Time picker; real notification delivered on the device | ✅ Verified on device |
| Rescheduled after restart / time-zone / DST | `RescheduleReceiver` and unit tests. Rescheduling after an **app update** was verified on the device. **A reboot was not tested on a device.** | ⚠️ Implemented and unit-tested. Do a reboot test before publishing (RELEASE_READINESS manual test M2) |
| Notice when notifications are off | Screenshot 04 (permission revoked) | ✅ Verified on device |
| Light/dark themes, large buttons | Screenshots 01/05; 64 dp primary button; 1.3× font check | ✅ Verified on device |
| No account; works offline | No login code; status and reminders are local (SharedPreferences + AlarmManager) | ✅ Code-verified |
| Records status only, no lock detection | In-app disclaimer | ✅ |
| May be a few minutes late | Device: 1m53s late with a 10-minute window | ✅ Typical. Deep Doze can reach about 1 h (not observed) |
| Contains ads | AdMob banner | ✅ |

Removed from earlier drafts because they could not be backed up:
- "screen reader support": TalkBack was not tested.
- "stored only on your phone": Android Auto Backup is enabled (`allowBackup="true"`, default rules), so preferences can be included in the user's Google backup.

## Release notes ("What's new")

The text is in `docs/RELEASE_NOTES.md` (468/500 characters). Its line "keep working after restarts" depends on the same unverified reboot test as above. Run that test, or soften the line to "are rescheduled automatically after restarts".

## Asset checklist

All files are in `artwork/play-store/` and were validated 2026-10-13 (real PNG signature, exact dimensions, no transparency, under Play limits).

| Asset | File | Status |
|---|---|---|
| App icon 512 × 512 | `icon-512.png` (35 855 B) | ✅ Ready |
| Feature graphic 1024 × 500 | `feature-graphic.png` (180 317 B) | ✅ Ready |
| Phone screenshots (2–8) | `screenshot-01…05-*.png`, 1080 × 1920 (361–428 KB each) | ✅ 5 ready, real 1.2.0 captures |
| 7"/10" tablet screenshots | – | Optional; not created (no tablet available) |
| Privacy policy URL | https://superadmir.blogspot.com/2025/07/privacy-policy-for-door-reminder-app.html (linked in the app menu) | ⚠️ Confirm it describes AdMob/UMP data use |
| Data safety form | – | ❌ **Update required** |
| Ads declaration | "Contains ads" | ✅ Correct |
| Content rating | Everyone | ✅ Unchanged |
| Target audience | – | ⚠️ Make sure children are not a target audience (ads are not configured for child-directed treatment) |
