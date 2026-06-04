# Fabiodalez Music — Android App

Android wrapper for [Monochrome](https://github.com/monochrome-music/monochrome), a privacy-respecting music streaming application.

This repository contains the Android wrapper, native bridges, build automation, and mobile-specific fixes used to package Monochrome as a Capacitor Android app.

## Features

- **Background playback** — Foreground Service keeps audio playing when the screen is off
- **Media controls** — Play/pause/skip in the notification shade, lock screen, and Bluetooth
- **Downloads** — Saves tracks to `Downloads/FabiodalezMusic/` with Android notifications
- **Local files** — Native Android folder picker for local music playback
- **OAuth** — Last.fm/Libre.fm authentication through Chrome Custom Tabs
- **Clipboard bridge** — Native copy-to-clipboard support
- **Bluetooth handling** — Playback controls and disconnect handling through the Android audio service
- **Mobile UI fixes** — Safe-area padding, larger touch targets, improved grid sizing, and Android back behavior
- **Streaming instance bootstrap** — Preloads working HiFi API streaming instances for better fresh-install playback
- **Security defaults** — Backup disabled, cleartext traffic disabled, and external browser bridge URL validation
- **Branding** — Fabiodalez Music name, icon, splash screen, and Android packaging

## Native Audio Roadmap

The current app improves WebView playback, but the actual audio pipeline still depends on the WebView/browser engine. That means Android battery optimization, JavaScript GC pauses, Web Audio scheduling, browser resampling, renderer suspension, or renderer memory pressure can still affect playback.

Long term, the project should move the critical playback path into a native audio service. WebView should become the UI/controller, while native code owns decoding, buffering, audio output, MediaSession state, and playback recovery.

See the full design plan: [`docs/native-audio-engine.md`](docs/native-audio-engine.md).

Recommended first native milestone:

> A native service and bridge that can play a generated PCM sine wave for 30 minutes without underruns while the screen is off.

Only after that works should the project add local-file decode, FFmpeg/libavcodec support, and streaming playback handoff.

## Requirements

The build script is currently written for macOS/Homebrew paths.

- macOS
- Git
- Node.js + npm
- JDK 21: `brew install openjdk@21`
- Android command-line tools: `brew install --cask android-commandlinetools`
- Android SDK platform/build tools installed through `sdkmanager`

The script expects these paths by default:

```bash
/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
/opt/homebrew/share/android-commandlinetools
```

If your tools are installed somewhere else, edit `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` near the top of `build-android.sh`.

## Quick Start

```bash
# 1. Clone Monochrome
git clone https://github.com/monochrome-music/monochrome.git
cd monochrome
git remote rename origin upstream

# 2. Clone this Android wrapper repo
cd ..
git clone https://github.com/emmandesu/Monochrome-Android-APK.git

# 3. Install wrapper files into the Monochrome clone
cd Monochrome-Android-APK
chmod +x install.sh
./install.sh ../monochrome

# 4. Build the APK from the Monochrome clone
cd ../monochrome
./build-android.sh
```

The debug APK will be copied to:

```text
Monochrome-debug.apk
```

## Updating Monochrome

After the wrapper has been installed into the Monochrome clone, run:

```bash
cd monochrome
./build-android.sh
```

The script fetches the latest `upstream/main`, applies Android build patches, builds the APK, then cleans up temporary upstream files. The upstream web app stays clean after the build.

## How It Works

The build script temporarily patches upstream files during build:

- `index.html` — injects Android scripts, logger, mobile viewport behavior, brand text, and CDN preconnect hints
- `package.json` — fixes broken dependency overrides and adds Capacitor requirements
- `js/storage.js` — adds working streaming instance fallbacks
- `js/app.js` — improves search debounce behavior
- `js/ui.js` — enriches album search results with cover/artist data when available from tracks
- `js/cache.js` — applies cache/query normalization improvements when the upstream pattern is present
- `js/HiFi.ts` — applies streaming/search fixes when the upstream pattern is present

All patches are reverted automatically at the end of the build.

## Project Structure

```text
Monochrome-Android-APK/
├── docs/
│   └── native-audio-engine.md      # Native playback architecture roadmap
├── android/
│   ├── android-service.js          # WebView-side Android bridge and UI fixes
│   ├── fm-logger.js                # Early console logger injection
│   └── app/
│       ├── build.gradle            # Android app module config
│       └── src/main/
│           ├── AndroidManifest.xml # Permissions, activity, service, provider
│           ├── java/com/monochrome/app/
│           │   ├── MainActivity.java
│           │   ├── AudioForegroundService.java
│           │   ├── AudioServicePlugin.java
│           │   ├── DownloadBridge.java
│           │   ├── LocalFilesBridge.java
│           │   ├── AndroidBridge.java
│           │   └── TidalWebViewClient.java
│           └── res/                # Icons, splash, strings, styles
├── build-android.sh                # Main build automation
├── capacitor.config.ts             # Capacitor app/server/plugin config
└── install.sh                      # Copies wrapper files into a Monochrome clone
```

## Troubleshooting

### `git fetch upstream` fails

Inside the Monochrome clone, make sure the upstream remote exists:

```bash
git remote -v
```

If it is missing:

```bash
git remote add upstream https://github.com/monochrome-music/monochrome.git
```

### JDK or Android SDK not found

Edit the environment variables near the top of `build-android.sh` so they match your machine:

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-commandlinetools
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

### APK builds but playback fails

Check Android Studio Logcat or Chrome WebView debugging:

```text
chrome://inspect
```

Useful tags/classes to filter:

- `MainActivity`
- `AudioForegroundService`
- `AudioServicePlugin`
- `TidalWebViewClient`
- `DownloadBridge`
- `LocalFilesBridge`

### Downloads do not appear

Downloads should be saved under:

```text
Downloads/FabiodalezMusic/
```

On Android 10 and newer this uses MediaStore. On older Android versions, storage permissions may be required.

## Recommended Next Improvements

- Add a native audio service shell and Capacitor `NativeAudio` plugin
- Add a native PCM/sine-wave playback smoke test before attempting streaming
- Add a GitHub Actions workflow to run a lightweight lint/build check on every push
- Add a release workflow for signed APK/AAB artifacts
- Move hardcoded Homebrew paths into environment checks with friendly error messages
- Add screenshots/GIFs to show the Android UI, splash screen, media notification, and local files picker
- Consider a `release` build type with ProGuard/R8 rules once the app is stable

## License

Same as [Monochrome](https://github.com/monochrome-music/monochrome/blob/main/license).
