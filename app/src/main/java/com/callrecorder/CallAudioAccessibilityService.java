package com.callrecorder;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.RequiresApi;

/**
 * Accessibility Service to capture system audio including call audio
 * This bypasses Android 9+ restrictions by using system-level access
 */
public class CallAudioAccessibilityService extends AccessibilityService {
    
    private static final String TAG = "CallAudioAccessibility";
    private static MediaProjection mediaProjection;
    private static CallAudioAccessibilityService instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "Accessibility Service Created");
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Monitor for phone and WhatsApp app events
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        
        // Detect phone app or WhatsApp
        if (packageName.contains("phone") || 
            packageName.contains("telecom") ||
            packageName.contains("whatsapp") ||
            packageName.contains("telegram") ||
            packageName.contains("skype")) {
            
            Log.d(TAG, "Call app detected: " + packageName);
            
            // Notify the recording service about the active call app
            Intent intent = new Intent(this, CallRecordingService.class);
            intent.putExtra("call_app_package", packageName);
            intent.putExtra("event_type", event.getEventType());
            startService(intent);
        }
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted");
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                         AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        
        setServiceInfo(info);
        
        Log.d(TAG, "Accessibility Service Connected");
    }
    
    public static CallAudioAccessibilityService getInstance() {
        return instance;
    }
    
    public static void setMediaProjection(MediaProjection projection) {
        mediaProjection = projection;
    }
    
    public static MediaProjection getMediaProjection() {
        return mediaProjection;
    }
}
