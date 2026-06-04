package com.monochrome.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase-1 native audio smoke-test service.
 *
 * This deliberately does NOT decode streams yet. It proves the native service,
 * foreground lifecycle, wake-lock behavior, audio focus, and PCM output path by
 * playing a generated sine wave from a dedicated Android audio thread.
 */
public class NativeAudioToneService extends Service {

    private static final String TAG = "NativeAudioToneService";

    public static final String ACTION_START_TONE = "com.monochrome.app.nativeaudio.START_TONE";
    public static final String ACTION_STOP_TONE = "com.monochrome.app.nativeaudio.STOP_TONE";
    public static final String ACTION_STATE = "com.monochrome.app.nativeaudio.STATE";

    private static final String CHANNEL_ID = "native_audio_engine";
    private static final int NOTIFICATION_ID = 42;

    private static final int DEFAULT_SAMPLE_RATE = 48000;
    private static final double DEFAULT_FREQUENCY_HZ = 440.0;
    private static final float DEFAULT_VOLUME = 0.08f;
    private static final long DEFAULT_DURATION_MS = 30L * 60L * 1000L;

    private static final Object STATE_LOCK = new Object();
    private static volatile String state = "idle";
    private static volatile String lastError = null;
    private static volatile long startedAtElapsedMs = 0L;
    private static volatile long durationMs = DEFAULT_DURATION_MS;
    private static volatile long framesWritten = 0L;
    private static volatile int underrunCount = 0;
    private static volatile int sampleRate = DEFAULT_SAMPLE_RATE;
    private static volatile double frequencyHz = DEFAULT_FREQUENCY_HZ;

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private Thread playbackThread;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    public static NativeAudioSnapshot snapshot() {
        synchronized (STATE_LOCK) {
            long elapsedMs = "playing".equals(state)
                    ? Math.max(0L, SystemClock.elapsedRealtime() - startedAtElapsedMs)
                    : 0L;
            return new NativeAudioSnapshot(
                    state,
                    lastError,
                    elapsedMs,
                    durationMs,
                    framesWritten,
                    underrunCount,
                    sampleRate,
                    frequencyHz
            );
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP_TONE.equals(action)) {
            stopTone("stopped", null);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START_TONE.equals(action)) {
            int requestedSampleRate = intent.getIntExtra("sampleRate", DEFAULT_SAMPLE_RATE);
            double requestedFrequency = intent.getDoubleExtra("frequencyHz", DEFAULT_FREQUENCY_HZ);
            float requestedVolume = intent.getFloatExtra("volume", DEFAULT_VOLUME);
            long requestedDuration = intent.getLongExtra("durationMs", DEFAULT_DURATION_MS);

            sampleRate = clampInt(requestedSampleRate, 8000, 192000, DEFAULT_SAMPLE_RATE);
            frequencyHz = clampDouble(requestedFrequency, 20.0, 20000.0, DEFAULT_FREQUENCY_HZ);
            requestedVolume = clampFloat(requestedVolume, 0.0f, 1.0f, DEFAULT_VOLUME);
            durationMs = Math.max(1000L, requestedDuration);

            startForegroundCompat(buildNotification("Starting native audio test…"));
            startToneThread(sampleRate, frequencyHz, requestedVolume, durationMs);
            return START_STICKY;
        }

        startForegroundCompat(buildNotification("Native audio engine ready"));
        return START_STICKY;
    }

    private void startToneThread(int sr, double freq, float volume, long maxDurationMs) {
        stopTone("restarting", null);
        stopRequested.set(false);

        updateState("playing", null, true);
        acquireWakeLock();
        requestAudioFocus();
        updateNotification("Playing native PCM test tone");

        playbackThread = new Thread(() -> runToneLoop(sr, freq, volume, maxDurationMs), "NativeAudioToneThread");
        playbackThread.setPriority(Thread.MAX_PRIORITY);
        playbackThread.start();
    }

    private void runToneLoop(int sr, double freq, float volume, long maxDurationMs) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

        AudioTrack track = null;
        try {
            int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
            int encoding = AudioFormat.ENCODING_PCM_16BIT;
            int minBufferBytes = AudioTrack.getMinBufferSize(sr, channelConfig, encoding);
            if (minBufferBytes <= 0) {
                throw new IllegalStateException("Invalid AudioTrack min buffer: " + minBufferBytes);
            }

            int bufferBytes = Math.max(minBufferBytes * 2, sr / 5 * 2 * 2); // at least ~200 ms stereo 16-bit
            track = buildAudioTrack(sr, channelConfig, encoding, bufferBytes);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioTrack failed to initialize");
            }

            int framesPerChunk = Math.max(256, Math.min(2048, sr / 50)); // ~20 ms chunk
            short[] pcm = new short[framesPerChunk * 2];
            double phase = 0.0;
            double phaseStep = 2.0 * Math.PI * freq / sr;
            short amplitude = (short) Math.max(0, Math.min(Short.MAX_VALUE, Short.MAX_VALUE * volume));

            synchronized (STATE_LOCK) {
                startedAtElapsedMs = SystemClock.elapsedRealtime();
                framesWritten = 0L;
                underrunCount = 0;
            }

            track.play();
            long endAt = SystemClock.elapsedRealtime() + maxDurationMs;

            while (!stopRequested.get() && SystemClock.elapsedRealtime() < endAt) {
                for (int i = 0; i < framesPerChunk; i++) {
                    short sample = (short) (Math.sin(phase) * amplitude);
                    int base = i * 2;
                    pcm[base] = sample;
                    pcm[base + 1] = sample;
                    phase += phaseStep;
                    if (phase >= 2.0 * Math.PI) {
                        phase -= 2.0 * Math.PI;
                    }
                }

                int written = track.write(pcm, 0, pcm.length, AudioTrack.WRITE_BLOCKING);
                if (written < 0) {
                    throw new IllegalStateException("AudioTrack write failed: " + written);
                }

                synchronized (STATE_LOCK) {
                    framesWritten += written / 2L;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        underrunCount = track.getUnderrunCount();
                    }
                }
            }

            if (!stopRequested.get()) {
                updateState("completed", null, true);
                updateNotification("Native audio test completed");
                stopForeground(false);
                stopSelf();
            }
        } catch (Exception e) {
            Log.e(TAG, "Native tone playback failed", e);
            updateState("error", e.getMessage(), true);
            updateNotification("Native audio test failed");
            stopSelf();
        } finally {
            if (track != null) {
                try {
                    track.pause();
                    track.flush();
                    track.release();
                } catch (Exception ignored) {
                    // AudioTrack may already be stopped/released on some OEM builds.
                }
            }
            releaseWakeLock();
            abandonAudioFocus();
        }
    }

    private AudioTrack buildAudioTrack(int sr, int channelConfig, int encoding, int bufferBytes) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sr)
                .setEncoding(encoding)
                .setChannelMask(channelConfig)
                .build();

        return new AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build();
    }

    private void stopTone(String nextState, String error) {
        stopRequested.set(true);
        Thread thread = playbackThread;
        playbackThread = null;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        releaseWakeLock();
        abandonAudioFocus();
        updateState(nextState, error, true);
        updateNotification("Native audio engine stopped");
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(focusChange -> {
                            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                                stopTone("stopped", "Audio focus lost");
                                stopSelf();
                            }
                        })
                        .build();
                audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                //noinspection deprecation
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception e) {
            Log.w(TAG, "requestAudioFocus failed", e);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
                audioFocusRequest = null;
            } else {
                //noinspection deprecation
                audioManager.abandonAudioFocus(null);
            }
        } catch (Exception e) {
            Log.w(TAG, "abandonAudioFocus failed", e);
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "FabiodalezMusic:NativeAudioTone");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(durationMs + 60_000L);
            }
        } catch (Exception e) {
            Log.w(TAG, "WakeLock acquire failed", e);
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            Log.w(TAG, "WakeLock release failed", e);
        } finally {
            wakeLock = null;
        }
    }

    private void updateState(String newState, String error, boolean broadcast) {
        synchronized (STATE_LOCK) {
            state = newState;
            lastError = error;
            if ("playing".equals(newState)) {
                startedAtElapsedMs = SystemClock.elapsedRealtime();
                framesWritten = 0L;
                underrunCount = 0;
            }
        }
        if (broadcast) sendStateBroadcast();
    }

    private void sendStateBroadcast() {
        NativeAudioSnapshot s = snapshot();
        Intent intent = new Intent(ACTION_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra("state", s.state);
        intent.putExtra("error", s.error);
        intent.putExtra("elapsedMs", s.elapsedMs);
        intent.putExtra("durationMs", s.durationMs);
        intent.putExtra("framesWritten", s.framesWritten);
        intent.putExtra("underrunCount", s.underrunCount);
        intent.putExtra("sampleRate", s.sampleRate);
        intent.putExtra("frequencyHz", s.frequencyHz);
        sendBroadcast(intent);
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 420,
                openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, NativeAudioToneService.class);
        stopIntent.setAction(ACTION_STOP_TONE);
        PendingIntent stopPending = PendingIntent.getService(this, 421,
                stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String title = String.format(Locale.US, "Native audio %.0f Hz", frequencyHz);
        NativeAudioSnapshot s = snapshot();
        String content = text + " • underruns: " + s.underrunCount;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(openPending)
                .setOngoing("playing".equals(s.state))
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .addAction(android.R.drawable.ic_media_pause, "Stop test", stopPending)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Native Audio Engine",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Native PCM playback test and future native audio engine");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopTone("idle", null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        if (value < min || value > max) return fallback;
        return value;
    }

    private static double clampDouble(double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || value < min || value > max) return fallback;
        return value;
    }

    private static float clampFloat(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || value < min || value > max) return fallback;
        return value;
    }

    public static class NativeAudioSnapshot {
        public final String state;
        public final String error;
        public final long elapsedMs;
        public final long durationMs;
        public final long framesWritten;
        public final int underrunCount;
        public final int sampleRate;
        public final double frequencyHz;

        NativeAudioSnapshot(String state, String error, long elapsedMs, long durationMs,
                            long framesWritten, int underrunCount, int sampleRate,
                            double frequencyHz) {
            this.state = state;
            this.error = error;
            this.elapsedMs = elapsedMs;
            this.durationMs = durationMs;
            this.framesWritten = framesWritten;
            this.underrunCount = underrunCount;
            this.sampleRate = sampleRate;
            this.frequencyHz = frequencyHz;
        }
    }
}
