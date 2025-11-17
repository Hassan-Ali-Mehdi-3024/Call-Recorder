package com.callrecorder;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

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
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private File outputFile;
    private MediaProjection mediaProjection;
    private Context context;
    private FileOutputStream outputStream;
    private long totalBytesWritten = 0;
    
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
            outputStream = new FileOutputStream(outputFile);
            writeWavHeader(outputStream, 0);
        } catch (IOException e) {
            Log.e(TAG, "Unable to create output stream: " + e.getMessage());
            return;
        }
        totalBytesWritten = 0;
        
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
        
        while (isRecording && outputStream != null) {
            try {
                int bytesRead = audioRecord.read(audioData, 0, bufferSize);
                int micBytesRead = 0;
                if (microphoneRecord != null) {
                    micBytesRead = microphoneRecord.read(micData, 0, micData.length);
                }
                
                if (bytesRead > 0) {
                    byte[] mixedAudio = mixAudioSources(audioData, bytesRead, micData, micBytesRead);
                    outputStream.write(mixedAudio);
                    totalBytesWritten += mixedAudio.length;
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
            finalizeWavHeader();
            RecordingStorageManager.scanFile(context, outputFile);
            
            Log.i(TAG, "Recording stopped and saved: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage(), e);
        }
    }
    
    public boolean isRecording() {
        return isRecording;
    }

    private void writeWavHeader(FileOutputStream out, long totalAudioLen) throws IOException {
        long totalDataLen = totalAudioLen + 36;
        long byteRate = SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8;
        byte[] header = new byte[44];

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        writeInt(header, 4, (int) totalDataLen);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        writeInt(header, 16, 16);
        writeShort(header, 20, (short) 1);
        writeShort(header, 22, (short) CHANNEL_COUNT);
        writeInt(header, 24, SAMPLE_RATE);
        writeInt(header, 28, (int) byteRate);
        writeShort(header, 32, (short) (CHANNEL_COUNT * BITS_PER_SAMPLE / 8));
        writeShort(header, 34, (short) BITS_PER_SAMPLE);
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        writeInt(header, 40, (int) totalAudioLen);

        out.write(header, 0, 44);
    }

    private void finalizeWavHeader() {
        if (outputFile == null || !outputFile.exists()) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
            long totalAudioLen = totalBytesWritten;
            long totalDataLen = totalAudioLen + 36;
            long byteRate = SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8;

            raf.seek(4);
            raf.writeInt(Integer.reverseBytes((int) totalDataLen));
            raf.seek(40);
            raf.writeInt(Integer.reverseBytes((int) totalAudioLen));
            raf.seek(28);
            raf.writeInt(Integer.reverseBytes((int) byteRate));
            raf.seek(32);
            raf.writeShort(Short.reverseBytes((short) (CHANNEL_COUNT * BITS_PER_SAMPLE / 8)));
            raf.seek(34);
            raf.writeShort(Short.reverseBytes((short) BITS_PER_SAMPLE));
        } catch (IOException e) {
            Log.e(TAG, "Failed to finalize WAV header: " + e.getMessage());
        }
    }

    private void closeOutputStream() {
        if (outputStream != null) {
            try {
                outputStream.flush();
                outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing output stream: " + e.getMessage());
            }
            outputStream = null;
        }
    }

    private void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >> 8) & 0xff);
        data[offset + 2] = (byte) ((value >> 16) & 0xff);
        data[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    private void writeShort(byte[] data, int offset, short value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >> 8) & 0xff);
    }
}
