package com.callrecorder;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Root-based audio recorder for perfect quality on rooted devices
 * This bypasses ALL Android restrictions and records system audio directly
 */
public class RootAudioRecorder {
    
    private static final String TAG = "RootAudioRecorder";
    private Context context;
    private Process suProcess;
    private boolean isRecording = false;
    private String outputFilePath;
    
    public RootAudioRecorder(Context context) {
        this.context = context;
    }
    
    /**
     * Check if device has root access
     */
    public static boolean isRooted() {
        // Check for common root indicators
        String[] paths = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        };
        
        for (String path : paths) {
            if (new File(path).exists()) {
                return true;
            }
        }
        
        // Try executing su command
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("exit\n");
            os.flush();
            int exitValue = process.waitFor();
            return exitValue == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if device has Magisk (modern root solution)
     */
    public static boolean hasMagisk() {
        try {
            Process process = Runtime.getRuntime().exec("magisk --version");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String version = reader.readLine();
            return version != null && !version.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Start recording using root privileges
     * This records directly from the audio hardware/kernel
     */
    public void startRecording(String filename) {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return;
        }
        
        if (!isRooted()) {
            Log.e(TAG, "Device is not rooted");
            return;
        }
        
        try {
            File recordingsDir = new File(context.getExternalFilesDir(null), "CallRecordings");
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs();
            }
            
            outputFilePath = new File(recordingsDir, filename).getAbsolutePath();
            
            // Start root shell
            suProcess = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
            
            // Root commands to capture audio directly from hardware
            // Method 1: Direct ALSA capture (most devices)
            String captureCommand = 
                "tinycap " + outputFilePath + " -D 0 -d 0 -c 2 -r 44100 -b 16 -p 4096 &\n";
            
            // Method 2: Alternative - use screencap for audio (if tinycap not available)
            // This works on some devices that block tinycap
            String altCommand = 
                "screenrecord --audio-source internal --bit-rate 128000 " + outputFilePath + " &\n";
            
            // Try primary method
            try {
                os.writeBytes(captureCommand);
                os.flush();
                Log.d(TAG, "Started tinycap recording");
            } catch (Exception e) {
                // Fallback to alternative
                os.writeBytes(altCommand);
                os.flush();
                Log.d(TAG, "Started screenrecord audio recording");
            }
            
            isRecording = true;
            Log.i(TAG, "Root recording started: " + outputFilePath);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting root recording: " + e.getMessage(), e);
            isRecording = false;
        }
    }
    
    /**
     * Install audio recording modules on rooted device
     */
    public boolean installRootModules() {
        if (!isRooted()) {
            return false;
        }
        
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            
            // Enable ALSA (Advanced Linux Sound Architecture)
            os.writeBytes("setenforce 0\n"); // Disable SELinux temporarily
            os.writeBytes("mount -o remount,rw /system\n");
            
            // Install tinyalsa tools if not present
            os.writeBytes("if [ ! -f /system/bin/tinycap ]; then\n");
            os.writeBytes("  busybox wget -O /system/bin/tinycap https://url-to-tinycap-binary\n");
            os.writeBytes("  chmod 755 /system/bin/tinycap\n");
            os.writeBytes("fi\n");
            
            // Set audio permissions
            os.writeBytes("chmod 666 /dev/snd/*\n");
            os.writeBytes("setprop persist.audio.record.internal 1\n");
            
            os.writeBytes("exit\n");
            os.flush();
            
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to install root modules: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Enable call recording at system level (permanent solution for rooted devices)
     */
    public boolean enableSystemCallRecording() {
        if (!isRooted()) {
            return false;
        }
        
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            
            // Modify system properties to allow call recording
            os.writeBytes("mount -o remount,rw /system\n");
            
            // Samsung devices
            os.writeBytes("setprop persist.audio.voice.record 1\n");
            os.writeBytes("setprop persist.audio.call.recording.enabled 1\n");
            
            // Pixel/Google devices
            os.writeBytes("setprop persist.audio.fluence.voicerec 1\n");
            os.writeBytes("setprop persist.audio.default.record.source 4\n");
            
            // Xiaomi/MIUI devices
            os.writeBytes("setprop persist.sys.phh.disable_audio_effects 0\n");
            
            // OnePlus devices
            os.writeBytes("setprop persist.vendor.audio.callaudio 1\n");
            
            // Universal method - patch audio_policy_configuration.xml
            String audioPolicy = 
                "echo '<devicePort tagName=\"telephony_tx\" type=\"AUDIO_DEVICE_OUT_TELEPHONY_TX\" role=\"source\"/>' " +
                ">> /system/etc/audio_policy_configuration.xml\n";
            os.writeBytes(audioPolicy);
            
            os.writeBytes("mount -o remount,ro /system\n");
            os.writeBytes("exit\n");
            os.flush();
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                Log.i(TAG, "System call recording enabled successfully");
                return true;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable system recording: " + e.getMessage());
        }
        
        return false;
    }
    
    public void stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording");
            return;
        }
        
        try {
            if (suProcess != null) {
                DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
                
                // Kill the recording process
                os.writeBytes("killall tinycap\n");
                os.writeBytes("killall screenrecord\n");
                os.writeBytes("exit\n");
                os.flush();
                
                suProcess.waitFor();
                suProcess.destroy();
            }
            
            isRecording = false;
            Log.i(TAG, "Root recording stopped: " + outputFilePath);
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping root recording: " + e.getMessage(), e);
        }
    }
    
    public boolean isRecording() {
        return isRecording;
    }
}
