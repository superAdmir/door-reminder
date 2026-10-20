# Google Play assets — Door Reminder 1.2.0

All assets were validated on 2026-10-13 against Google Play's limits. Every upload file was confirmed to be a **real PNG**: the PNG file signature was checked, it decodes without errors, and it has no transparency. No format conversion was needed, so the files were not replaced. The unframed originals stay in `raw/` and the layout sources in `source/`. Screenshots must be 320–3840 px per side, the long side at most 2× the short side, and ≤ 8 MB. The feature graphic must be exactly 1024 × 500. The icon must be exactly 512 × 512 and ≤ 1 MB.

## Inventory

| File | Dimensions | Format | Size | Play Console location |
|---|---|---|---|---|
| `icon-512.png` | 512 × 512 | PNG, fully opaque | 35 KB | Main store listing → **App icon** |
| `feature-graphic.png` | 1024 × 500 | PNG 24-bit, no alpha | 180 KB | Main store listing → **Feature graphic** |
| `screenshot-01-locked-light.png` | 1080 × 1920 | PNG 24-bit | 402 KB | Main store listing → **Phone screenshots** (1st) |
| `screenshot-02-unlocked-undo.png` | 1080 × 1920 | PNG 24-bit | 414 KB | Phone screenshots (2nd) |
| `screenshot-03-reminder-time.png` | 1080 × 1920 | PNG 24-bit | 361 KB | Phone screenshots (3rd) |
| `screenshot-04-notifications-off.png` | 1080 × 1920 | PNG 24-bit | 428 KB | Phone screenshots (4th) |
| `screenshot-05-dark-theme.png` | 1080 × 1920 | PNG 24-bit | 390 KB | Phone screenshots (5th) |
| `raw/01…05_*.png` | 1080 × 2340 | PNG | 136–174 KB | Unframed originals; not for upload |
| `source/*.html` | – | HTML | – | Layout sources (re-render with headless Chrome at 1080×1920 / 1024×500) |

## Screenshot content (all real captures)

| # | Heading / subline | Captured state |
|---|---|---|
| 1 | "Mark your door as locked" / "One tap, with the time you last updated it." | Locked, light theme, daily reminder 21:00 |
| 2 | "Locked or unlocked at a glance" / "Tapped by mistake? Undo it." | Unlocked, with the real "Marked as unlocked · Undo" snackbar |
| 3 | "Set a daily reminder" / "Pick a time that fits your routine." | Real Material time picker (24 h clock dial) |
| 4 | "Know if reminders can't appear" / "A clear notice when notifications are off." | Notification permission revoked; real in-app warning card |
| 5 | "Light and dark themes" / "Easy to read, day or night." | Same locked state, system dark theme |

### How they were captured
- **Device:** Samsung SM-S711B, Android 16, at its native 1080 × 2340 resolution, via `adb exec-out screencap`. The UI was driven by adb taps on the real controls.
- **Build:** release build (R8, versionCode 4), created with:
  ```
  ./gradlew assembleRelease -PdoorReminder.allowTestAdsInRelease=true -PdoorReminder.screenshotMode=true
  ```
  - `screenshotMode` leaves the banner ad unit empty, so **no ad is requested or shown**. The rest of the UI is the normal release UI, with no "(debug)" label.
  - This build cannot be uploaded: it depends on the test-ads QA flag, which the release guard rejects.
- **Not shown:** no ads, debug labels, notifications, or personal data. The status bar is the real device status bar (Samsung ignores SystemUI demo mode); it shows only the clock, Wi-Fi, signal, mute and battery icons.
- **Cleanup:** the phone's settings (dark mode, font scale, demo mode) were restored, and the QA build was uninstalled.
- **Composition:** each capture was placed unaltered inside a simple dark phone frame on the brand gradient (`#3A6FC4` → `#1D4488`). Headings use Montserrat, the app's own font (`app/src/main/res/font/montserrat_regular.ttf`).

## Icon
- Design: a white arched door with a green "locked" badge on brand blue. It matches the app's "Marked as locked" colour.
- Master: `artwork/icon/icon-master.svg`; geometry: `artwork/icon/icon_geometry.py`.
- Android launcher resources use the same design:
  - adaptive vector foreground, background and monochrome layers;
  - `mipmap-anydpi-v26`;
  - legacy `mipmap-*/ic_launcher*.webp`.
- Verified on the device: App info shows the new icon.
- **Legacy rasters (Android 7.0–7.1, API 24–25): fixed 2026-10-13.** They had rendered as square tiles because the clip shape used the wrong coordinate space (now fixed in `icon_geometry.py`).
  - They now follow the legacy convention: 48 dp canvas with the shape inset 2 dp (44 dp). `ic_launcher.webp` is a rounded square (10 % radius); `ic_launcher_round.webp` is a circle. Both are transparent outside the shape.
  - Sizes: mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192 px; lossless WebP, 1.8–9.6 KB.
  - The design is unchanged. The adaptive icon and `icon-512.png` were not modified.

## Feature graphic
App icon, the name "Door Reminder", and the line "Mark your door as locked in one tap and get a daily reminder to check." The right side shows the real locked-state screenshot (#1) in the same phone frame.
