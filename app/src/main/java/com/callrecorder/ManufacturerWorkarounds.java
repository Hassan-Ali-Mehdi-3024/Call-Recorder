package com.callrecorder;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * Device-specific workarounds for manufacturers that block call recording
 * Samsung Knox, Google Pixel, OnePlus, etc.
 */
public class ManufacturerWorkarounds {
    
    private static final String TAG = "ManufacturerWorkarounds";
    private Context context;
    
    public ManufacturerWorkarounds(Context context) {
        this.context = context;
    }
    
    /**
     * Detect device manufacturer and model
     */
    public static String getManufacturer() {
        return Build.MANUFACTURER.toLowerCase();
    }
    
    public static String getModel() {
        return Build.MODEL.toLowerCase();
    }
    
    /**
     * Apply manufacturer-specific workarounds
     */
    public boolean applyWorkarounds() {
        String manufacturer = getManufacturer();
        
        Log.d(TAG, "Applying workarounds for: " + manufacturer);
        
        boolean success = false;
        
        if (manufacturer.contains("samsung")) {
            success = applySamsungWorkaround();
        } else if (manufacturer.contains("google") || manufacturer.contains("pixel")) {
            success = applyPixelWorkaround();
        } else if (manufacturer.contains("oneplus")) {
            success = applyOnePlusWorkaround();
        } else if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            success = applyXiaomiWorkaround();
        } else if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
            success = applyOppoWorkaround();
        } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            success = applyHuaweiWorkaround();
        }
        
        return success;
    }
    
    /**
     * Samsung Knox bypass for call recording
     */
    private boolean applySamsungWorkaround() {
        Log.d(TAG, "Applying Samsung/Knox workaround");
        
        try {
            if (RootAudioRecorder.isRooted()) {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                
                // Disable Samsung Knox security restrictions
                os.writeBytes("pm disable com.samsung.android.knox.containeragent\n");
                os.writeBytes("pm disable com.samsung.android.knox.attestation\n");
                os.writeBytes("pm disable com.sec.android.app.knoxcontainer\n");
                
                // Enable call recording on Samsung
                os.writeBytes("setprop persist.audio.voice.record 1\n");
                os.writeBytes("setprop persist.audio.call.recording.enabled 1\n");
                
                // Modify CSC (Consumer Software Customization) settings
                os.writeBytes("mount -o remount,rw /system\n");
                os.writeBytes("echo 'true' > /system/csc/others.xml\n");
                
                os.writeBytes("exit\n");
                os.flush();
                
                return process.waitFor() == 0;
            } else {
                // Non-root workaround: Use MediaProjection with accessibility
                Log.d(TAG, "Non-root Samsung workaround: Using MediaProjection");
                return true; // Will use MediaProjection API
            }
        } catch (Exception e) {
            Log.e(TAG, "Samsung workaround failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Google Pixel bypass (very restrictive)
     */
    private boolean applyPixelWorkaround() {
        Log.d(TAG, "Applying Pixel workaround");
        
        try {
            if (RootAudioRecorder.isRooted()) {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                
                // Modify Pixel audio policies
                os.writeBytes("setprop persist.audio.fluence.voicerec 1\n");
                os.writeBytes("setprop persist.audio.default.record.source 4\n"); // VOICE_CALL source
                
                // Disable Google's audio restrictions
                os.writeBytes("pm disable com.google.android.apps.restore\n");
                
                // Patch audio_effects.xml
                os.writeBytes("mount -o remount,rw /system\n");
                String audioEffectsPath = "/system/etc/audio_effects.xml";
                os.writeBytes("sed -i 's/AUDIO_SOURCE_VOICE_CALL=false/AUDIO_SOURCE_VOICE_CALL=true/g' " 
                    + audioEffectsPath + "\n");
                
                os.writeBytes("exit\n");
                os.flush();
                
                return process.waitFor() == 0;
            } else {
                // Pixel requires MediaProjection + Accessibility for best results
                Log.d(TAG, "Pixel non-root: Using enhanced MediaProjection");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Pixel workaround failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * OnePlus (OxygenOS) bypass
     */
    private boolean applyOnePlusWorkaround() {
        Log.d(TAG, "Applying OnePlus workaround");
        
        try {
            if (RootAudioRecorder.isRooted()) {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                
                // OnePlus-specific audio properties
                os.writeBytes("setprop persist.vendor.audio.callaudio 1\n");
                os.writeBytes("setprop persist.vendor.audio.voicecall.speaker.stereo 1\n");
                
                // Disable OxygenOS restrictions
                os.writeBytes("mount -o remount,rw /system\n");
                os.writeBytes("mount -o remount,rw /vendor\n");
                
                // Modify vendor audio policy
                String vendorPolicy = "/vendor/etc/audio_policy_configuration.xml";
                os.writeBytes("if [ -f " + vendorPolicy + " ]; then\n");
                os.writeBytes("  sed -i 's/AUDIO_DEVICE_IN_TELEPHONY_RX/AUDIO_DEVICE_IN_VOICE_CALL/g' " 
                    + vendorPolicy + "\n");
                os.writeBytes("fi\n");
                
                os.writeBytes("exit\n");
                os.flush();
                
                return process.waitFor() == 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "OnePlus workaround failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Xiaomi/MIUI workaround (usually easier)
     */
    private boolean applyXiaomiWorkaround() {
        Log.d(TAG, "Applying Xiaomi/MIUI workaround");
        
        try {
            if (RootAudioRecorder.isRooted()) {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                
                // MIUI usually has built-in call recording, just need to enable it
                os.writeBytes("setprop persist.sys.phh.disable_audio_effects 0\n");
                os.writeBytes("setprop persist.audio.fluence.voicecall 1\n");
                
                // Enable call recording in MIUI settings database
                os.writeBytes("settings put system call_record_mode 1\n");
                
                os.writeBytes("exit\n");
                os.flush();
                
                return process.waitFor() == 0;
            } else {
                // MIUI is generally more permissive, should work with standard methods
                Log.d(TAG, "Xiaomi device should support call recording natively");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Xiaomi workaround failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Oppo/Realme (ColorOS) workaround
     */
    private boolean applyOppoWorkaround() {
        Log.d(TAG, "Applying Oppo/Realme workaround");
        
        try {
            if (RootAudioRecorder.isRooted()) {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                
                // ColorOS call recording properties
                os.writeBytes("setprop persist.sys.phh.disable_audio_effects 0\n");
                os.writeBytes("setprop ro.config.record_call 1\n");
                
                os.writeBytes("exit\n");
                os.flush();
                
                return process.waitFor() == 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Oppo workaround failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Huawei/Honor (EMUI) workaround
     */
    private boolean applyHuaweiWorkaround() {
        Log.d(TAG, "Applying Huawei/Honor workaround");
        
        try {
            if (RootAudioRecorder.isRooted()) {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                
                // EMUI call recording
                os.writeBytes("setprop persist.audio.voice.record 1\n");
                os.writeBytes("setprop ro.config.hw_voicerecord 1\n");
                
                os.writeBytes("exit\n");
                os.flush();
                
                return process.waitFor() == 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Huawei workaround failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Check if device requires special workaround
     */
    public static boolean requiresWorkaround() {
        String manufacturer = getManufacturer();
        return manufacturer.contains("samsung") || 
               manufacturer.contains("google") || 
               manufacturer.contains("pixel") ||
               manufacturer.contains("oneplus");
    }
}
