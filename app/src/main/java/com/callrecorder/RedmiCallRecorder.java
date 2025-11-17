package com.callrecorder;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * High-fidelity recorder tuned specifically for Redmi 10C / MIUI 13.
 * Uses AudioRecord with VOICE_RECOGNITION (or VOICE_CALL) to capture both sides.
 */
public class RedmiCallRecorder {

    private static final String TAG = "RedmiCallRecorder";
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CHANNEL_COUNT = 1;
    private static final long PCM_LOG_INTERVAL_MS = 2000;

    private static class SourceOption {
        final int source;
        final String label;
        final boolean forceSpeakerphone;

        SourceOption(int source, String label, boolean forceSpeakerphone) {
            this.source = source;
            this.label = label;
            this.forceSpeakerphone = forceSpeakerphone;
        }
    }

    private final Context context;
    private final AudioManager audioManager;
    private AudioRecord audioRecord;
    private Thread writerThread;
    private boolean isRecording = false;
    private WavFileWriter wavWriter;
    private File outputFile;
    private AudioFocusRequest focusRequest;
    private long lastPcmLogTimeMs = 0;
    private String activeSourceLabel = "unknown";
    private boolean speakerphoneForced = false;

    private static final SourceOption[] AUDIO_SOURCES = new SourceOption[] {
        new SourceOption(MediaRecorder.AudioSource.VOICE_CALL, "VOICE_CALL", false),
        new SourceOption(MediaRecorder.AudioSource.VOICE_RECOGNITION, "VOICE_RECOGNITION", false),
        new SourceOption(MediaRecorder.AudioSource.VOICE_COMMUNICATION, "VOICE_COMMUNICATION", false),
        new SourceOption(MediaRecorder.AudioSource.MIC, "MIC_SPEAKER", true)
    };

    public RedmiCallRecorder(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean startRecording(File destinationFile) {
        if (isRecording) {
            return true;
        }
        outputFile = destinationFile;
        if (outputFile == null) {
            Log.e(TAG, "Destination file is null");
            return false;
        }

        int bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AudioFormat.ENCODING_PCM_16BIT
        );
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        bufferSize *= 2; // Safety margin

        audioRecord = createAudioRecord(bufferSize);
        if (audioRecord == null) {
            Log.e(TAG, "Failed to initialize AudioRecord for Redmi");
            return false;
        }

        try {
            wavWriter = new WavFileWriter(outputFile, SAMPLE_RATE, CHANNEL_COUNT, BITS_PER_SAMPLE);
        } catch (IOException e) {
            Log.e(TAG, "Unable to open WAV writer: " + e.getMessage());
            audioRecord.release();
            audioRecord = null;
            return false;
        }

        requestAudioFocus();
        audioRecord.startRecording();
        isRecording = true;

        final int finalBufferSize = bufferSize;
        writerThread = new Thread(() -> writeLoop(finalBufferSize), "RedmiRecorderThread");
        writerThread.start();
        Log.i(TAG, "Redmi recorder started: " + outputFile.getAbsolutePath());
        return true;
    }

    private AudioRecord createAudioRecord(int bufferSize) {
        for (SourceOption option : AUDIO_SOURCES) {
            try {
                configureRoutingForSource(option);
                AudioRecord record = new AudioRecord(
                    option.source,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                );
                if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                    activeSourceLabel = option.label;
                    Log.i(TAG, "AudioRecord initialized with source: " + option.label);
                    return record;
                } else {
                    record.release();
                }
            } catch (Exception e) {
                Log.w(TAG, "Audio source " + option.label + " failed: " + e.getMessage());
            }
        }
        resetSpeakerphoneRouting();
        return null;
    }

    private void writeLoop(int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        while (isRecording && audioRecord != null && wavWriter != null) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read > 0) {
                logPcmAmplitude(buffer, read);
                try {
                    wavWriter.write(buffer, read);
                } catch (IOException e) {
                    Log.e(TAG, "Error writing WAV data: " + e.getMessage());
                    break;
                }
            }
        }
    }

    public void stopRecording() {
        if (!isRecording) {
            return;
        }
        isRecording = false;

        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {}
            audioRecord.release();
            audioRecord = null;
        }

        if (writerThread != null) {
            try {
                writerThread.join(500);
            } catch (InterruptedException ignored) {}
            writerThread = null;
        }

        if (wavWriter != null) {
            try {
                wavWriter.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close WAV writer: " + e.getMessage());
            }
            wavWriter = null;
        }

        abandonAudioFocus();
        RecordingStorageManager.scanFile(context, outputFile);
        Log.i(TAG, "Redmi recorder stopped");
    }

    public boolean isRecording() {
        return isRecording;
    }

    private void requestAudioFocus() {
        if (audioManager == null) {
            return;
        }
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAcceptsDelayedFocusGain(false)
                .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
                focusRequest = null;
            }
        } else {
            audioManager.abandonAudioFocus(null);
        }
        audioManager.setMode(AudioManager.MODE_NORMAL);
        resetSpeakerphoneRouting();
    }

    private void configureRoutingForSource(SourceOption option) {
        if (audioManager == null) {
            return;
        }
        if (option.forceSpeakerphone && !audioManager.isSpeakerphoneOn()) {
            audioManager.setSpeakerphoneOn(true);
            speakerphoneForced = true;
            Log.i(TAG, "Speakerphone forced ON for source " + option.label);
        } else if (!option.forceSpeakerphone && speakerphoneForced) {
            audioManager.setSpeakerphoneOn(false);
            speakerphoneForced = false;
            Log.i(TAG, "Speakerphone routing reset for non-speaker source");
        }
    }

    private void resetSpeakerphoneRouting() {
        if (audioManager == null || !speakerphoneForced) {
            return;
        }
        audioManager.setSpeakerphoneOn(false);
        speakerphoneForced = false;
        Log.i(TAG, "Speakerphone forced routing disabled");
    }

    private void logPcmAmplitude(byte[] buffer, int length) {
        long avg = computeAverageAmplitude(buffer, length);
        long now = SystemClock.elapsedRealtime();
        if (now - lastPcmLogTimeMs >= PCM_LOG_INTERVAL_MS) {
            Log.d(TAG, "PCM avg=" + avg + " source=" + activeSourceLabel);
            if (avg < 10) {
                Log.w(TAG, "PCM amplitude near zero - MIUI may be blocking downlink audio");
            }
            lastPcmLogTimeMs = now;
        }
    }

    private long computeAverageAmplitude(byte[] buffer, int length) {
        if (length <= 0) {
            return 0;
        }
        long sum = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += Math.abs(sample);
        }
        int samples = Math.max(1, length / 2);
        return sum / samples;
    }
}
