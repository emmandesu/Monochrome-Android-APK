# Changelog

## 2.9.2

Android wrapper maintenance release after reviewing old **2.8.1** APK logs.

### Fixed

- Stabilized Android search build patches against repeated upstream `400` responses.
- Removed the risky unified-search `artists.profileArt` include that could be rejected by `tidal-proxy.monochrome.tf`.
- Added defensive cleanup so previously patched unified search includes are normalized during Android builds.
- Reduced direct HiFi/Tidal search limits to `50` where upstream code still used `100`.
- Simplified the Android build patch flow to make future upstream syncs easier to maintain.

### Notes

- This release should be rebuilt before comparing new logs. Old `2.8.1` logs still show `limit=100` and do not include these fixes.
- The native audio service remains a generated-PCM smoke test only; real decode and streaming handoff are still future phases.

## 2.9.1

Android wrapper release aligned after upstream **v2.8.1: Fix Tidal proxy (`tidal-proxy.monochrome.tf`)**.

### Added

- Added native audio engine roadmap in `docs/native-audio-engine.md`.
- Added `NativeAudioToneService`, a foreground native PCM tone playback service for the first native-audio smoke test.
- Added `NativeAudioPlugin`, exposing native tone test commands to the WebView through Capacitor.
- Added native audio tone test guide in `docs/native-audio-tone-test.md`.

### Fixed

- Fixed Android status bar overlap by switching Capacitor config to the official `StatusBar` plugin and forcing WebView layout below system bars.
- Fixed Android WebView auth fetch fragility by proxying `auth.monochrome.tf` through the native WebView client with CORS headers.
- Fixed upstream audio-proxy dependency by unwrapping `audio-proxy.binimum.org/proxy-audio/` URLs back to raw Tidal URLs, then applying the native Tidal origin fix.
- Reduced Android-injected search limit from `100` to `50` to avoid repeated upstream `400` search responses.
- Hardened external browser URL handling to allow only `http` and `https` schemes.
- Hardened Android manifest defaults by disabling backup and cleartext traffic.
- Sanitized native download filenames before saving.

### Improved

- Added extra Android build-time preconnect/DNS hints for upstream auth, API, Tidal, uptime, and audio proxy services.
- Documented current native-audio migration path and future Rust/FFmpeg/Oboe direction.
- Updated README with native-audio roadmap and Android wrapper architecture notes.

### Notes

- The native audio service currently plays generated PCM only. It does not decode or stream real music yet.
- WebView playback remains the default path until native local-file decode and streaming handoff are implemented.
- The next recommended phase is local-file decode through a native PCM ring-buffer path.
