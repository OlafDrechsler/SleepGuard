# SleepGuard

**Stops your phone from running all night when you fall asleep to videos or
audio — so the battery isn't empty by morning and you don't lose your place.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

SleepGuard has two modes:

- **Video:** a small red circle appears on top of whatever you are watching, at
  an interval you choose. Tap it to confirm you are awake. Don't tap it — because
  you have fallen asleep — and SleepGuard locks the screen, like the power button.
- **Audio:** a plain sleep timer for music, audiobooks and podcasts. After the
  interval, playback simply stops — also with the screen off.

## Why?

- **YouTube & autoplay:** Without SleepGuard, one video starts the next, all
  night long. Your battery drains to empty and your morning alarm can no longer
  ring. SleepGuard breaks the chain as soon as you stop responding.
- **Netflix, Prime Video & co.:** Fall asleep mid-episode and you wake up not
  knowing where you dozed off — scrubbing back, sometimes through long ads again
  and again. SleepGuard locks the screen right where you fell asleep.

## Features

- Two modes: **Video** (wake-check button) and **Audio** (sleep timer that stops playback, even with the screen off)
- Guided first-run setup that explains each permission
- Adjustable interval and reaction time; eight button positions
- Reliable background timing via the system `AlarmManager`
- Material 3 interface with dynamic colours and dark mode
- 29 interface languages (all 24 official EU languages plus RU, JA, ZH-Hans, ZH-Hant, TR)
- No ads, no tracking, **no internet permission at all**

## Screenshots

<!-- Screenshots liegen unter fastlane/metadata/android/en-US/images/ -->
_Screenshots: see `fastlane/metadata/android/en-US/images/`._

## Permissions

| Permission | Why |
|---|---|
| Display over other apps | Show the red check button over your video |
| Device admin (screen lock only) | Lock the screen like the power button |
| Foreground service / exact alarm | Make the button appear on time |
| Ignore battery optimization (optional) | Keep the timer reliable in the background |
| Notifications (Android 13+) | Show the "SleepGuard is active" ongoing notification |

The app collects **no data** — see [Privacy Policy](docs/privacy-policy.md).

## Building from source

```bash
# Debug build
./gradlew assembleDebug

# Signed release (needs keystore.properties — see keystore.properties.example)
./gradlew assembleRelease   # APK, for GitHub / F-Droid
./gradlew bundleRelease     # AAB, for the Play Store
```

On Windows use `.\gradlew.bat` instead of `./gradlew`, run from the project root
in PowerShell or cmd.

Requirements: Android SDK with `compileSdk 35`, JDK 17. Built with Kotlin and
Jetpack Compose; only Apache-2.0-licensed AndroidX libraries are used — no
proprietary dependencies.

## Releases

- **F-Droid:** built and signed by F-Droid from this source.
- **GitHub Releases:** signed APK attached to each release.
- **Google Play:** distributed as an AAB via Play App Signing.

See [PUBLISHING.md](PUBLISHING.md) for the full release process.

## Support development

SleepGuard is free and will stay free. If it helps you, you can leave a voluntary
tip via the **Support development** button in the app
([paypal.me/OLEobjekt](https://paypal.me/OLEobjekt)). There is deliberately no
reward in return — it's simply a thank-you.

## License

SleepGuard is free software, licensed under the
[GNU General Public License v3.0 or later](LICENSE).

Copyright (C) 2026 Olaf Drechsler
