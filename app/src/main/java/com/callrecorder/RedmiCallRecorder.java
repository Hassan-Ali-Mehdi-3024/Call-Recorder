package com.callrecorder;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
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

    private final Context context;
    private final AudioManager audioManager;
    private AudioRecord audioRecord;
    private Thread writerThread;
    private boolean isRecording = false;
    private WavFileWriter wavWriter;
    private File outputFile;
    private AudioFocusRequest focusRequest;

    private static final int[] AUDIO_SOURCES = new int[] {
        MediaRecorder.AudioSource.VOICE_CALL,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.MIC
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
        for (int source : AUDIO_SOURCES) {
            try {
                AudioRecord record = new AudioRecord(
                    source,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                );
                if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "AudioRecord initialized with source: " + source);
                    return record;
                } else {
                    record.release();
                }
            } catch (Exception e) {
                Log.w(TAG, "Audio source " + source + " failed: " + e.getMessage());
            }
        }
        return null;
    }

    private void writeLoop(int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        while (isRecording && audioRecord != null && wavWriter != null) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read > 0) {
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
    }
}
