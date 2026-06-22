# SleepGuard

**Locks your phone when you fall asleep watching videos — so the battery isn't
empty by morning and you never lose your place in a film again.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

SleepGuard shows a small red circle on top of whatever you are watching, at an
interval you choose. Tap it and playback continues. Don't tap it — because you
have fallen asleep — and SleepGuard locks the screen, just like pressing the
power button.

## Why?

- **YouTube & autoplay:** Without SleepGuard, one video starts the next, all
  night long. Your battery drains to empty and your morning alarm can no longer
  ring. SleepGuard breaks the chain as soon as you stop responding.
- **Netflix, Prime Video & co.:** Fall asleep mid-episode and you wake up not
  knowing where you dozed off — scrubbing back, sometimes through long ads again
  and again. SleepGuard locks the screen right where you fell asleep.

## Features

- Works on top of any video or streaming app
- Adjustable interval and reaction time
- Eight button positions
- Reliable background timing via the system `AlarmManager`
- 9 interface languages (EN, DE, FR, ES, IT, PT, NL, PL, TR)
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

Requirements: Android SDK with `compileSdk 34`. Only Apache-2.0-licensed AndroidX
libraries are used — no proprietary dependencies.

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
