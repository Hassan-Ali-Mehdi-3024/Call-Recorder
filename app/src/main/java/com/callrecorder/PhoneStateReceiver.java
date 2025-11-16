package com.callrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PhoneStateReceiver extends BroadcastReceiver {
    
    private static final String TAG = "PhoneStateReceiver";
    private static AudioRecorder audioRecorder;
    private static boolean isRecording = false;
    private static String lastPhoneNumber = "";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (action == null) {
            return;
        }
        
        if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            
            if (phoneNumber != null) {
                lastPhoneNumber = phoneNumber;
            }
            
            handlePhoneStateChange(context, state);
        } else if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            if (phoneNumber != null) {
                lastPhoneNumber = phoneNumber;
            }
            Log.d(TAG, "Outgoing call to: " + phoneNumber);
        }
    }
    
    private void handlePhoneStateChange(Context context, String state) {
        if (state == null) {
            return;
        }
        
        // Use String literals instead of constants for switch-case
        if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
            Log.d(TAG, "Phone is ringing");
            // Call incoming, prepare to record
        } else if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
            Log.d(TAG, "Call answered/active");
            // Call is active, start recording
            if (!isRecording) {
                startRecording(context);
            }
        } else if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
            Log.d(TAG, "Call ended");
            // Call ended, stop recording
            if (isRecording) {
                stopRecording(context);
            }
        }
    }
    
    private void startRecording(Context context) {
        try {
            if (audioRecorder == null) {
                audioRecorder = new AudioRecorder(context);
                
                // Get MediaProjection if available from accessibility service
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    android.media.projection.MediaProjection projection = 
                        CallAudioAccessibilityService.getMediaProjection();
                    if (projection != null) {
                        audioRecorder.setMediaProjection(projection);
                        Log.d(TAG, "Using MediaProjection for enhanced recording");
                    }
                }
            }
            
            String filename = AudioRecorder.generateFileName(lastPhoneNumber);
            audioRecorder.startRecording(filename);
            isRecording = true;
            
            Log.d(TAG, "Recording started: " + filename);
            
            // Update service notification
            updateServiceNotification(context, "Recording call...");
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage());
            isRecording = false;
        }
    }
    
    private void stopRecording(Context context) {
        try {
            if (audioRecorder != null) {
                audioRecorder.stopRecording();
                isRecording = false;
                Log.d(TAG, "Recording stopped");
                
                // Update service notification
                updateServiceNotification(context, "Call recording saved");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage());
        }
    }
    
    private void updateServiceNotification(Context context, String message) {
        Intent intent = new Intent(context, CallRecordingService.class);
        intent.putExtra("notification_message", message);
        context.startService(intent);
    }
}
