package com.callrecorder;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AudioRecorder {
    
    private static final String TAG = "AudioRecorder";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private File outputFile;
    private Context context;
    
    // Alternative: MediaRecorder for better compatibility
    private MediaRecorder mediaRecorder;
    private boolean useMediaRecorder = true;
    
    // Advanced recording methods
    private MediaProjectionRecorder mediaProjectionRecorder;
    private RootAudioRecorder rootAudioRecorder;
    private MediaProjection mediaProjection;
    
    // Recording strategy priority
    private enum RecordingMethod {
        ROOT,                   // Best quality - requires root
        MEDIA_PROJECTION,       // Good quality - Android 10+
        VOICE_CALL,            // Medium quality - system privileges
        VOICE_COMMUNICATION,   // Medium quality - VoIP
        MICROPHONE             // Fallback - your voice only
    }
    
    public AudioRecorder(Context context) {
        this.context = context;
        initializeRecordingMethod();
    }
    
    public void setMediaProjection(MediaProjection projection) {
        this.mediaProjection = projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && projection != null) {
            mediaProjectionRecorder = new MediaProjectionRecorder(context, projection);
        }
    }
    
    private void initializeRecordingMethod() {
        // REDMI 10C OPTIMIZATION
        if (RedmiOptimizations.isRedmiDevice()) {
            RedmiOptimizations redmiOpt = new RedmiOptimizations(context);
            redmiOpt.applyRedmiOptimizations();
            Log.i(TAG, "Redmi 10C optimizations applied - MIUI native recording enabled!");
        } else {
            // Apply manufacturer-specific workarounds for other devices
            ManufacturerWorkarounds workarounds = new ManufacturerWorkarounds(context);
            workarounds.applyWorkarounds();
        }
        
        // Initialize root recorder if available
        if (RootAudioRecorder.isRooted()) {
            rootAudioRecorder = new RootAudioRecorder(context);
            Log.i(TAG, "Root access detected - using enhanced recording");
        }
    }
    
    public static String generateFileName(String phoneNumber) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = dateFormat.format(new Date());
        String sanitizedNumber = phoneNumber.replaceAll("[^0-9]", "");
        if (sanitizedNumber.isEmpty()) {
            sanitizedNumber = "unknown";
        }
        return "Call_" + sanitizedNumber + "_" + timestamp + ".3gp";
    }
    
    public void startRecording(String filename) {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return;
        }
        
        try {
            // Create recordings directory
            File recordingsDir = new File(context.getExternalFilesDir(null), "CallRecordings");
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs();
            }
            
            outputFile = new File(recordingsDir, filename);
            
            // Use best available recording method
            RecordingMethod method = selectBestRecordingMethod();
            Log.i(TAG, "Using recording method: " + method);
            
            switch (method) {
                case ROOT:
                    if (rootAudioRecorder != null) {
                        rootAudioRecorder.startRecording(filename);
                        isRecording = true;
                    }
                    break;
                    
                case MEDIA_PROJECTION:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjectionRecorder != null) {
                        mediaProjectionRecorder.startRecording(filename);
                        isRecording = true;
                    } else {
                        // Fallback to MediaRecorder
                        startMediaRecorderRecording();
                        isRecording = true;
                    }
                    break;
                    
                case VOICE_CALL:
                case VOICE_COMMUNICATION:
                case MICROPHONE:
                default:
                    if (useMediaRecorder) {
                        startMediaRecorderRecording();
                    } else {
                        startAudioRecordRecording();
                    }
                    isRecording = true;
                    break;
            }
            
            Log.i(TAG, "Recording started: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage(), e);
            isRecording = false;
        }
    }
    
    private RecordingMethod selectBestRecordingMethod() {
        // Priority 1: Root access (best quality)
        if (RootAudioRecorder.isRooted()) {
            return RecordingMethod.ROOT;
        }
        
        // Priority 2: MediaProjection (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
            return RecordingMethod.MEDIA_PROJECTION;
        }
        
        // Priority 3: Try VOICE_CALL (may not work on Android 9+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return RecordingMethod.VOICE_CALL;
        }
        
        // Priority 4: VOICE_COMMUNICATION (for VoIP)
        return RecordingMethod.VOICE_COMMUNICATION;
    }
    
    private void startMediaRecorderRecording() throws IOException {
        mediaRecorder = new MediaRecorder();
        
        // REDMI 10C OPTIMIZED CONFIGURATION
        if (RedmiOptimizations.isRedmiDevice()) {
            try {
                mediaRecorder.setAudioSource(RedmiOptimizations.getOptimalAudioSource());
                mediaRecorder.setOutputFormat(RedmiOptimizations.getOptimalOutputFormat());
                mediaRecorder.setAudioEncoder(RedmiOptimizations.getOptimalEncoder());
                mediaRecorder.setAudioSamplingRate(RedmiOptimizations.getOptimalSampleRate());
                mediaRecorder.setAudioEncodingBitRate(23850); // High quality
                mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
                mediaRecorder.prepare();
                mediaRecorder.start();
                Log.i(TAG, "Recording started with Redmi 10C optimized settings");
                return;
            } catch (Exception e) {
                Log.w(TAG, "Redmi optimized recording failed, trying fallback: " + e.getMessage());
            }
        }
        
        // Try multiple audio sources in order of quality for other devices
        int[] audioSources = {
            MediaRecorder.AudioSource.VOICE_CALL,           // Best - both sides (requires privileges)
            MediaRecorder.AudioSource.VOICE_DOWNLINK,       // Incoming audio only
            MediaRecorder.AudioSource.VOICE_UPLINK,         // Outgoing audio only
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // VoIP optimized
            MediaRecorder.AudioSource.CAMCORDER,            // Alternative
            MediaRecorder.AudioSource.MIC                   // Fallback
        };
        
        boolean initialized = false;
        for (int source : audioSources) {
            try {
                if (mediaRecorder != null) {
                    mediaRecorder.reset();
                }
                mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(source);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB); // Wide band for better quality
                mediaRecorder.setAudioSamplingRate(16000);
                mediaRecorder.setAudioEncodingBitRate(23850);
                mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
                mediaRecorder.prepare();
                mediaRecorder.start();
                initialized = true;
                Log.i(TAG, "Recording started with audio source: " + source);
                break;
            } catch (Exception e) {
                Log.w(TAG, "Audio source " + source + " failed: " + e.getMessage());
            }
        }
        
        if (!initialized) {
            throw new IOException("Failed to initialize any audio source");
        }
    }
    
    private void startAudioRecordRecording() {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        
        try {
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );
        } catch (Exception e) {
            Log.w(TAG, "VOICE_COMMUNICATION not available, using MIC");
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );
        }
        
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed");
            return;
        }
        
        audioRecord.startRecording();
        
        recordingThread = new Thread(() -> writeAudioDataToFile(bufferSize));
        recordingThread.start();
    }
    
    private void writeAudioDataToFile(int bufferSize) {
        byte[] audioData = new byte[bufferSize];
        FileOutputStream outputStream = null;
        
        try {
            outputStream = new FileOutputStream(outputFile);
            
            while (isRecording) {
                int bytesRead = audioRecord.read(audioData, 0, bufferSize);
                if (bytesRead > 0) {
                    outputStream.write(audioData, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing audio data: " + e.getMessage());
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing output stream: " + e.getMessage());
            }
        }
    }
    
    public void stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording");
            return;
        }
        
        isRecording = false;
        
        try {
            // Stop all possible recorders
            if (rootAudioRecorder != null && rootAudioRecorder.isRecording()) {
                rootAudioRecorder.stopRecording();
            }
            
            if (mediaProjectionRecorder != null && mediaProjectionRecorder.isRecording()) {
                mediaProjectionRecorder.stopRecording();
            }
            
            if (useMediaRecorder && mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            } else if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                
                if (recordingThread != null) {
                    recordingThread.join();
                }
            }
            
            Log.i(TAG, "Recording stopped and saved: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage(), e);
        }
    }
    
    public boolean isRecording() {
        return isRecording;
    }
}
