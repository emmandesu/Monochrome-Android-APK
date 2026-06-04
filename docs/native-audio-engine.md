# Native Audio Engine Roadmap

## Why this is needed

The current Android wrapper improves Monochrome playback with a foreground service, media notification controls, WebView hardening, and WebView-side audio hooks. That is useful, but it still leaves the actual audio pipeline inside the browser/WebView process.

That means playback can still be affected by:

- Android battery optimization throttling WebView work
- JavaScript engine GC pauses
- Web Audio scheduling stalls
- Browser audio context suspension
- WebView renderer memory pressure or renderer process kills
- Quality changes caused by browser resampling/mixing before Android output

The long-term fix is to move the critical playback path out of WebView. WebView should become the UI/controller, while a native playback service owns buffering, decoding, audio output, media session state, and recovery.

## Target Architecture

```text
Monochrome Web UI
    │
    │  Capacitor/JS bridge commands
    ▼
NativeAudioService / MediaSessionService
    │
    ├── Queue + state machine
    ├── Network fetcher / stream resolver adapter
    ├── Decoder layer
    │     ├── FFmpeg / libavcodec option
    │     └── platform decoder option where possible
    ├── PCM ring buffer
    ├── Audio output layer
    │     ├── Preferred Android path: Oboe / AAudio / OpenSL ES fallback
    │     └── Portable Rust path: CPAL where practical
    └── MediaSession + notification + headset/Bluetooth controls
```

## Recommended Direction

### Output engine

Prefer an Android-native output layer first:

- **Oboe** is the best Android-focused low-latency choice.
- On newer Android versions, Oboe uses **AAudio**.
- On older Android versions, Oboe falls back to **OpenSL ES**.

Rust can still be used for queueing, buffering, state, and decode orchestration, but Android audio output should not be boxed into CPAL only. CPAL is useful for portability, but Oboe gives better alignment with Android device-specific audio behavior.

### Decoder layer

Use a pluggable decoder interface:

- `FfmpegDecoder` for broad codec coverage.
- `PlatformDecoder` later for Android-native decode paths when suitable.
- `PassthroughDecoder` if the source is already compatible with the native output path.

Important: FFmpeg licensing must be handled carefully. Keep FFmpeg as a separate shared-library dependency, document the exact build configuration, and avoid GPL-only FFmpeg options unless the whole distribution is intended to become GPL-compatible.

### WebView responsibility

The WebView should keep doing what it is good at:

- Search UI
- Library UI
- Account/login flow
- Playlist/queue editing
- Metadata display
- Sending play/pause/seek/next/previous commands

The WebView should stop being responsible for:

- Long-running audio decoding
- Audio scheduling
- Audio output
- Real-time buffer delivery
- Playback survival during renderer pressure

## Proposed JS Bridge API

```ts
NativeAudio.load({
  id: string,
  title: string,
  artist: string,
  album?: string,
  artworkUrl?: string,
  streamUrl: string,
  headers?: Record<string, string>,
  durationMs?: number
})

NativeAudio.play()
NativeAudio.pause()
NativeAudio.stop()
NativeAudio.seekTo({ positionMs: number })
NativeAudio.setQueue({ tracks: NativeTrack[], startIndex: number })
NativeAudio.skipToNext()
NativeAudio.skipToPrevious()
NativeAudio.setVolume({ volume: number })
NativeAudio.setRepeatMode({ mode: 'off' | 'one' | 'all' })
NativeAudio.setShuffle({ enabled: boolean })
NativeAudio.getState()
```

## Native State Events Back to WebView

```ts
NativeAudio.addListener('stateChanged', {
  state: 'idle' | 'loading' | 'buffering' | 'playing' | 'paused' | 'ended' | 'error',
  positionMs: number,
  durationMs?: number,
  bufferedMs?: number,
  error?: string
})

NativeAudio.addListener('trackChanged', {
  id: string,
  title: string,
  artist: string,
  artworkUrl?: string
})
```

## Threading Model

```text
UI / WebView thread
    └── Sends commands only

Native service main thread
    └── Owns lifecycle, MediaSession, notification, and command routing

Network thread pool
    └── Fetches stream data and fills compressed-data buffers

Decode thread
    └── Converts compressed data into PCM

Real-time audio callback thread
    └── Pulls already-decoded PCM from a lock-free/ring buffer
```

Rules:

- Never decode inside the audio callback.
- Never allocate in the audio callback.
- Never perform network I/O in the audio callback.
- Never call back into WebView from the audio callback.
- Use bounded ring buffers to avoid unbounded memory growth.
- Keep state transitions atomic and observable.

## Migration Plan

### Phase 0 — Current state hardening

- Keep the existing WebView playback path as the default.
- Add this native audio roadmap.
- Track native engine design decisions in GitHub issues.

### Phase 1 — Native service shell

- Add `NativeAudioService` or migrate to `MediaSessionService`.
- Add a Capacitor plugin named `NativeAudio`.
- Implement no-op command/state flow first.
- Keep WebView audio as fallback.

### Phase 2 — Local PCM smoke test

- Add a native output engine that plays generated PCM/sine wave.
- Validate service survival with screen off, app backgrounded, and recents dismissed.
- Validate notification/headset/Bluetooth controls.

### Phase 3 — Local file decode

- Add decoder interface.
- Decode a local file to PCM.
- Feed PCM into the native output ring buffer.
- Compare glitches against the WebView path.

### Phase 4 — Streaming decode

- Add stream fetching with headers/cookies where required.
- Add buffering strategy.
- Add seek handling.
- Add reconnect/retry handling.

### Phase 5 — WebView handoff

- WebView resolves metadata/stream candidate.
- Native engine owns playback after `NativeAudio.load()`.
- WebView observes native state events.
- Existing Web Audio path remains as fallback.

### Phase 6 — Quality and reliability testing

Measure:

- Gap/glitch count during screen-off playback
- Buffer underruns
- Renderer kill recovery
- Memory pressure behavior
- CPU use
- Battery use
- Output sample rate and resampling behavior
- Bluetooth route changes
- Headset media button behavior

## Build Notes

A Rust-based engine would likely need:

```text
rust/
├── Cargo.toml
├── src/lib.rs
└── build.rs

android/app/src/main/jniLibs/
├── arm64-v8a/libnativeaudio.so
├── armeabi-v7a/libnativeaudio.so
└── x86_64/libnativeaudio.so
```

Suggested tooling options:

- `cargo-ndk` for Android Rust cross-compilation
- JNI bridge from Kotlin/Java into Rust
- Oboe through C++ directly, or Rust bindings only after validating stability
- FFmpeg as a separately built shared dependency

## Risks / Decisions Needed

- **Licensing:** FFmpeg configuration decides whether the app remains LGPL-compatible or becomes GPL-bound.
- **Streaming auth:** Native fetcher needs access to the same tokens/headers/cookies currently used by the WebView/proxy flow.
- **DRM:** Native playback cannot bypass DRM. Only streams the app is legitimately allowed to play should be handed to the native engine.
- **Codec support:** Broad codec support increases binary size and licensing complexity.
- **Device fragmentation:** Android audio behavior varies by OEM, Android version, sample rate, and output route.
- **Fallback path:** WebView playback should stay available until native playback proves stable.

## First Implementation Target

The first real code milestone should not be streaming. It should be:

> A native service and bridge that can play a generated PCM sine wave for 30 minutes without underruns while the screen is off.

Only after that works should the project add FFmpeg/local-file decoding and then streaming.

## References

- Android Oboe audio library: https://developer.android.com/games/sdk/oboe
- Android Media3 background playback service: https://developer.android.com/media/media3/session/background-playback
- CPAL Rust audio crate: https://docs.rs/cpal/latest/cpal/
- FFmpeg license notes: https://ffmpeg.org/legal.html
