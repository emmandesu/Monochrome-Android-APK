# Native Audio Tone Test

This is the first native-audio milestone: prove that Android native code can own a long-running PCM output path independently of Web Audio.

This test does **not** decode real music yet. It plays a generated sine wave through `AudioTrack` from a foreground media playback service.

## What was added

- `NativeAudioPlugin` — Capacitor plugin exposed to WebView as `NativeAudio`
- `NativeAudioToneService` — foreground service that owns the native PCM test path
- `AudioTrack` streaming output with stereo 16-bit PCM
- Dedicated high-priority audio thread using `THREAD_PRIORITY_AUDIO`
- Partial wake lock during the test
- Audio focus request
- Foreground media playback notification with a stop action
- State snapshots with elapsed time, frames written, underrun count, sample rate, and frequency

## Build

From the Monochrome clone where the wrapper has been installed:

```bash
./build-android.sh
```

Install the generated APK on a device.

## Start the 30-minute native PCM smoke test

Open Chrome on your desktop and inspect the Android WebView:

```text
chrome://inspect
```

In the DevTools console, run:

```js
await window.Capacitor.Plugins.NativeAudio.startToneTest({
  sampleRate: 48000,
  frequencyHz: 440,
  volume: 0.08,
  durationMs: 30 * 60 * 1000
})
```

You should hear a quiet 440 Hz sine tone and see a **Native Audio Engine** foreground notification.

## Check state

```js
await window.Capacitor.Plugins.NativeAudio.getState()
```

Expected fields:

```js
{
  state: 'playing',
  error: null,
  elapsedMs: 12345,
  durationMs: 1800000,
  framesWritten: 592560,
  underrunCount: 0,
  sampleRate: 48000,
  frequencyHz: 440
}
```

## Listen for state changes

```js
const NativeAudio = window.Capacitor.Plugins.NativeAudio

const listener = await NativeAudio.addListener('stateChanged', state => {
  console.log('NativeAudio state:', state)
})
```

Remove the listener later:

```js
await listener.remove()
```

## Stop the test

```js
await window.Capacitor.Plugins.NativeAudio.stopToneTest()
```

You can also stop it from the Android notification action.

## Screen-off reliability test

1. Start the 30-minute test.
2. Turn the screen off.
3. Leave the device locked for 30 minutes.
4. Unlock the device.
5. Run:

```js
await window.Capacitor.Plugins.NativeAudio.getState()
```

Pass criteria:

- The foreground notification remains visible while playing.
- Audio continues while the screen is off.
- `state` becomes `completed` after the duration ends.
- `underrunCount` stays at `0` or very low.
- No crash appears in Logcat.

## Logcat filters

Useful filters:

```text
NativeAudioToneService
NativeAudioPlugin
AudioTrack
AudioFlinger
AudioService
```

Example:

```bash
adb logcat | grep -E "NativeAudioToneService|NativeAudioPlugin|AudioTrack|AudioFlinger"
```

## Notes

- This proves the native service/output lifecycle first.
- This does not yet prove decoding, streaming, FFmpeg, Rust/JNI, or gapless playback.
- If this test is stable, the next phase is a native local-file decode pipeline.
