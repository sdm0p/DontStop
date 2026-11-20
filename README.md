# DONTSTOP

**Keep going. We're watching.**

DONTSTOP is an Android digital-wellbeing app that tracks time in selected social apps and estimates swipes in short-video feeds. It gives escalating audio reminders and displays a small, draggable assistant ball while a tracked app is open.

## Release v1

- Android application ID: `com.dontstop`
- Version: `0.1.0` (version code `1`)
- Platform: Android; React Native and TypeScript UI with Kotlin tracking, overlay, storage, and audio services
- Current distribution: installable release APK built locally; no public Play Store release is configured
- APK output: `android/app/build/outputs/apk/release/app-release.apk` after building

This is an early device-tested release. Swipe totals are estimates because Instagram, YouTube, and Facebook do not expose a guaranteed one-event-per-finger-swipe signal. Device settings and manufacturer battery controls can affect background behavior and audio.

## Features

- Per-day foreground time tracking for enabled apps
- Estimated swipe tracking in Instagram Reels, YouTube Shorts, and Facebook Reels
- Shared or per-app limits and configurable reminder counts
- Escalating gentle, intermediate, and savage sound clips, with a continuous category after a limit
- Draggable assistant ball that docks to a screen edge
- Local dashboard, settings, and usage history
- Offline operation; no account or backend

## Requirements

- Android SDK and a connected Android device for device installation
- Node.js 22 or newer
- Java version supported by the Gradle wrapper (see Android Gradle configuration)
- Accessibility, Usage Access, and Display over other apps permissions for full tracking and overlay behavior

## Build

From the project root:

```powershell
npm ci
cd android
.
gradlew.bat assembleRelease
```

The APK is written to `android/app/build/outputs/apk/release/app-release.apk`.

To install it with Android Platform Tools:

```powershell
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

The release build uses a local `android/keystore.properties` when present. Keep the keystore and its passwords private and backed up. The keystore properties file and keystore files are git-ignored. Without a configured release keystore, Gradle may produce an unsigned APK that Android cannot install directly.

## First launch

1. Complete setup and choose connected apps, daily allowances, reminder counts, and silent/DND behavior.
2. Grant Usage Access, Accessibility, overlay, and notification permissions when prompted.
3. Keep DONTSTOP's Accessibility Service enabled in Android settings. Reinstalling after uninstalling clears app settings and permission grants.
4. Confirm the dashboard reports tracking as active before relying on reminders.

The silent/DND preference controls whether DONTSTOP suppresses reminders when the phone is silent or in Do Not Disturb. Android audio focus, device volume, routing, and manufacturer behavior can still affect playback; the app cannot guarantee that it overrides system controls.

## Sound files

Place bundled MP3 files in the matching Android asset folders:

```text
android/app/src/main/assets/audio/gentle/
android/app/src/main/assets/audio/intermediate/
android/app/src/main/assets/audio/savage/
android/app/src/main/assets/audio/continuous/
```

File names should be lowercase or mixed-case `.mp3`; clips are selected randomly within the stage folder, avoiding an immediately repeated clip when alternatives exist. Only distribute audio you have permission or a license to bundle.

## Development checks

```powershell
npm run typecheck
npm test
```

For a debug build:

```powershell
cd android
.gradlew.bat assembleDebug
```

## Privacy

DONTSTOP stores settings and daily usage aggregates locally. It does not send usage or screen content to a backend. Accessibility access is used to identify foreground apps and short-video surfaces; screen text and accessibility nodes are inspected transiently and are not stored. See [`android/NATIVE_PRIVACY.md`](android/NATIVE_PRIVACY.md).

## Project documents

- [`SPEC.md`](SPEC.md): product behavior and constraints
- [`DESIGN.md`](DESIGN.md): interface design notes
- [`TASKS.md`](TASKS.md): implementation checklist

## License

No project-wide open-source license has been added yet. Unless a license is added, the source is available for viewing in this repository but is not granted for reuse or redistribution. Third-party libraries and bundled audio remain subject to their own licenses and permissions.
