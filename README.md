# Sunrise Alarm

Offline Android alarm-clock app that computes NOAA sunrise from GPS-cached coordinates (Surat default) and schedules a must-fire `AlarmManager.setAlarmClock` alarm. Supports API 21–35.

## Features
- Must-fire sunrise via `setAlarmClock` (not the Clock app `ACTION_SET_ALARM`)
- One-shot foreground GPS via platform `LocationManager` (no Play Services, no background location)
- Boot/package-replaced reschedule from SharedPreferences cache only
- Ring UI with dismiss and 5-minute snooze
- Minimal deps: desugar for `java.time`, no WorkManager, no coroutines library, Jetifier off

## Permissions
Location (foreground), exact alarm (`USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`), full-screen intent, notifications (API 33+), vibrate, boot completed.

## Build APK without local Android tools
Push this repo to GitHub (or run the workflow manually). Open the repo on GitHub → **Actions** → **Build APK** → open the latest successful run → download the **app-release** artifact → unzip to get the release APK. The workflow runs `assembleRelease` (minify + shrink). No JDK or Android SDK install on your Mac is required.

### Release signing (Play Store)
If these repository secrets are all set, CI signs with your upload/production keystore:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64 of your `.jks` / `.keystore` (`base64 -i your.keystore \| pbcopy` on macOS) |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias |
| `RELEASE_KEY_PASSWORD` | Key password |

Add them under **Settings → Secrets and variables → Actions**. If any are missing, CI generates an ephemeral keystore so you still get an installable **app-release** APK — that build is not suitable for Play Store updates (use your own upload keystore secrets for Play-ready signing).

## Run
1. Open this folder in Android Studio (Ladybug+ recommended).
2. Sync Gradle (wrapper uses Gradle 8.9 / AGP 8.7.3).
3. Run on a device or emulator (API 21+).
4. Grant location and notifications when prompted. On API 31–32, allow exact alarms if asked. On API 34+, allow full-screen intent if prompted.
5. Turn on **Sunrise Auto-Alarm**, or tap **Schedule Next Sunrise Alarm**.

## Play listing notes
Declare exact alarms and full-screen intent as alarm-core functionality. Data safety: location used on-device only, not shared. Host a privacy policy URL when publishing.
