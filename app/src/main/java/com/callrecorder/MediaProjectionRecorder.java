package com.callrecorder;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;

/**
 * Advanced audio recorder using MediaProjection API (Android 10+)
 * This captures internal audio including the other party's voice
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
public class MediaProjectionRecorder {
    
    private static final String TAG = "MediaProjectionRecorder";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHANNEL_COUNT = 2;
    private static final int BITS_PER_SAMPLE = 16;
    private static final long PCM_LOG_INTERVAL_MS = 2000;
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private File outputFile;
    private MediaProjection mediaProjection;
    private Context context;
    private WavFileWriter wavWriter;
    private long lastPcmLogTimeMs = 0;
    
    // Dual recording: internal audio + microphone
    private AudioRecord microphoneRecord;
    
    public MediaProjectionRecorder(Context context, MediaProjection mediaProjection) {
        this.context = context;
        this.mediaProjection = mediaProjection;
    }
    
    public void startRecording(File destinationFile) {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return;
        }
        
        try {
            outputFile = destinationFile;
            if (outputFile == null) {
                Log.e(TAG, "Destination file is null");
                return;
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                startPlaybackCaptureRecording();
            } else {
                Log.e(TAG, "MediaProjection not available");
            }
            
            isRecording = true;
            Log.i(TAG, "MediaProjection recording started: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage(), e);
            isRecording = false;
        }
    }
    
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startPlaybackCaptureRecording() {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        
        // Build AudioPlaybackCaptureConfiguration
        AudioPlaybackCaptureConfiguration config =
            new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) // WhatsApp, VoIP
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA) // Media playback
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN) // Catch-all
                .build();
        
        // Create AudioFormat
        AudioFormat format = new AudioFormat.Builder()
            .setEncoding(AUDIO_FORMAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .build();
        
        // Create AudioRecord with playback capture
        audioRecord = new AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(config)
            .build();
        
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed");
            return;
        }
        
        try {
            wavWriter = new WavFileWriter(outputFile, SAMPLE_RATE, CHANNEL_COUNT, BITS_PER_SAMPLE);
        } catch (IOException e) {
            Log.e(TAG, "Unable to open WAV writer: " + e.getMessage());
            return;
        }
        
        audioRecord.startRecording();
        
        // Also record from microphone to mix both sources
        startMicrophoneRecording(bufferSize);
        
        recordingThread = new Thread(() -> writeAudioDataToFile(bufferSize));
        recordingThread.start();
    }
    
    private void startMicrophoneRecording(int bufferSize) {
        try {
            microphoneRecord = new AudioRecord(
                android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AUDIO_FORMAT,
                bufferSize
            );
            
            if (microphoneRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                microphoneRecord.startRecording();
                Log.d(TAG, "Microphone recording started for mixing");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start microphone: " + e.getMessage());
        }
    }
    
    private void writeAudioDataToFile(int bufferSize) {
        byte[] audioData = new byte[bufferSize];
        byte[] micData = new byte[bufferSize / 2]; // Mono for mic
        
        while (isRecording && wavWriter != null) {
            try {
                int bytesRead = audioRecord.read(audioData, 0, bufferSize);
                int micBytesRead = 0;
                if (microphoneRecord != null) {
                    micBytesRead = microphoneRecord.read(micData, 0, micData.length);
                }
                
                if (bytesRead > 0) {
                    long playbackAvg = computeAverageAmplitude(audioData, bytesRead);
                    long micAvg = micBytesRead > 0 ? computeAverageAmplitude(micData, micBytesRead) : -1;
                    byte[] mixedAudio = mixAudioSources(audioData, bytesRead, micData, micBytesRead);
                    long mixedAvg = computeAverageAmplitude(mixedAudio, mixedAudio.length);
                    logPcmAmplitudes(playbackAvg, micAvg, mixedAvg);
                    wavWriter.write(mixedAudio, mixedAudio.length);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error writing audio data: " + e.getMessage());
                break;
            }
        }
    }
    
    /**
     * Mix internal audio (stereo) with microphone audio (mono)
     * This ensures both parties are captured clearly
     */
    private byte[] mixAudioSources(byte[] playbackData, int playbackLength, 
                                   byte[] micData, int micLength) {
        byte[] result = new byte[playbackLength];
        
        // Copy playback data (internal audio - other party)
        System.arraycopy(playbackData, 0, result, 0, playbackLength);
        
        // Mix in microphone data (your voice)
        if (micLength > 0) {
            for (int i = 0; i < Math.min(micLength, playbackLength / 2); i += 2) {
                // Convert bytes to 16-bit samples
                short playbackSample = (short) ((result[i * 2 + 1] << 8) | (result[i * 2] & 0xFF));
                short micSample = (short) ((micData[i + 1] << 8) | (micData[i] & 0xFF));
                
                // Mix samples (average to prevent clipping)
                short mixedSample = (short) ((playbackSample + micSample) / 2);
                
                // Convert back to bytes
                result[i * 2] = (byte) (mixedSample & 0xFF);
                result[i * 2 + 1] = (byte) ((mixedSample >> 8) & 0xFF);
            }
        }
        
        return result;
    }
    
    public void stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording");
            return;
        }
        
        isRecording = false;
        
        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
            
            if (microphoneRecord != null) {
                microphoneRecord.stop();
                microphoneRecord.release();
                microphoneRecord = null;
            }
            
            if (recordingThread != null) {
                recordingThread.join();
            }
            closeOutputStream();
            RecordingStorageManager.scanFile(context, outputFile);
            
            Log.i(TAG, "Recording stopped and saved: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage(), e);
        }
    }
    
    public boolean isRecording() {
        return isRecording;
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

    private void logPcmAmplitudes(long playbackAvg, long micAvg, long mixedAvg) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastPcmLogTimeMs < PCM_LOG_INTERVAL_MS) {
            return;
        }
        lastPcmLogTimeMs = now;
        Log.d(TAG, "PCM avg playback=" + playbackAvg + " mic=" + micAvg + " mixed=" + mixedAvg);
        if (mixedAvg < 10 && playbackAvg < 10) {
            Log.w(TAG, "Playback PCM near zero - MediaProjection may lack consent or target app not capturable");
        }
    }

    private void closeOutputStream() {
        if (wavWriter != null) {
            try {
                wavWriter.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing WAV writer: " + e.getMessage());
            }
            wavWriter = null;
        }
    }
}
