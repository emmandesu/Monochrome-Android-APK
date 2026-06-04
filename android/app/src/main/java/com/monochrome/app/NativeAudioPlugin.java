package com.monochrome.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "NativeAudio")
public class NativeAudioPlugin extends Plugin {

    private BroadcastReceiver stateReceiver;

    @Override
    public void load() {
        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                notifyListeners("stateChanged", snapshotFromIntent(intent));
            }
        };

        IntentFilter filter = new IntentFilter(NativeAudioToneService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(stateReceiver, filter);
        }
    }

    @PluginMethod()
    public void startToneTest(PluginCall call) {
        int sampleRate = call.getInt("sampleRate", 48000);
        double frequencyHz = call.getData().optDouble("frequencyHz", 440.0);
        double volumeDouble = call.getData().optDouble("volume", 0.08);
        long durationMs = call.getData().optLong("durationMs", 30L * 60L * 1000L);

        Intent intent = new Intent(getContext(), NativeAudioToneService.class);
        intent.setAction(NativeAudioToneService.ACTION_START_TONE);
        intent.putExtra("sampleRate", sampleRate);
        intent.putExtra("frequencyHz", frequencyHz);
        intent.putExtra("volume", (float) volumeDouble);
        intent.putExtra("durationMs", durationMs);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getContext().startForegroundService(intent);
        } else {
            getContext().startService(intent);
        }

        JSObject result = new JSObject();
        result.put("started", true);
        result.put("sampleRate", sampleRate);
        result.put("frequencyHz", frequencyHz);
        result.put("durationMs", durationMs);
        call.resolve(result);
    }

    @PluginMethod()
    public void stopToneTest(PluginCall call) {
        Intent intent = new Intent(getContext(), NativeAudioToneService.class);
        intent.setAction(NativeAudioToneService.ACTION_STOP_TONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getContext().startForegroundService(intent);
        } else {
            getContext().startService(intent);
        }
        call.resolve(snapshotToJsObject(NativeAudioToneService.snapshot()));
    }

    @PluginMethod()
    public void getState(PluginCall call) {
        call.resolve(snapshotToJsObject(NativeAudioToneService.snapshot()));
    }

    private JSObject snapshotFromIntent(Intent intent) {
        JSObject data = new JSObject();
        data.put("state", intent.getStringExtra("state"));
        data.put("error", intent.getStringExtra("error"));
        data.put("elapsedMs", intent.getLongExtra("elapsedMs", 0L));
        data.put("durationMs", intent.getLongExtra("durationMs", 0L));
        data.put("framesWritten", intent.getLongExtra("framesWritten", 0L));
        data.put("underrunCount", intent.getIntExtra("underrunCount", 0));
        data.put("sampleRate", intent.getIntExtra("sampleRate", 0));
        data.put("frequencyHz", intent.getDoubleExtra("frequencyHz", 0.0));
        return data;
    }

    private JSObject snapshotToJsObject(NativeAudioToneService.NativeAudioSnapshot snapshot) {
        JSObject data = new JSObject();
        data.put("state", snapshot.state);
        data.put("error", snapshot.error);
        data.put("elapsedMs", snapshot.elapsedMs);
        data.put("durationMs", snapshot.durationMs);
        data.put("framesWritten", snapshot.framesWritten);
        data.put("underrunCount", snapshot.underrunCount);
        data.put("sampleRate", snapshot.sampleRate);
        data.put("frequencyHz", snapshot.frequencyHz);
        return data;
    }

    @Override
    protected void handleOnDestroy() {
        if (stateReceiver != null) {
            try {
                getContext().unregisterReceiver(stateReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered by the framework/activity lifecycle.
            }
            stateReceiver = null;
        }
    }
}
