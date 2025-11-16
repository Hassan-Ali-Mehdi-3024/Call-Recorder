package com.callrecorder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MEDIA_PROJECTION_REQUEST_CODE = 101;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 102;
    private static final int ACCESSIBILITY_REQUEST_CODE = 103;
    
    private Switch switchAutoRecord;
    private Button btnCheckPermissions;
    private Button btnEnableAccessibility;
    private Button btnRequestMediaProjection;
    private Button btnApplyRootFixes;
    private TextView tvStatus;
    private TextView tvDeviceInfo;
    private RecyclerView recyclerViewRecordings;
    private RecordingsAdapter adapter;
    
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupListeners();
        checkAndRequestPermissions();
        loadRecordings();
        
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }
    
    private void initializeViews() {
        switchAutoRecord = findViewById(R.id.switchAutoRecord);
        btnCheckPermissions = findViewById(R.id.btnCheckPermissions);
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility);
        btnRequestMediaProjection = findViewById(R.id.btnRequestMediaProjection);
        btnApplyRootFixes = findViewById(R.id.btnApplyRootFixes);
        tvStatus = findViewById(R.id.tvStatus);
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        recyclerViewRecordings = findViewById(R.id.recyclerViewRecordings);
        
        recyclerViewRecordings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordingsAdapter(new ArrayList<>());
        recyclerViewRecordings.setAdapter(adapter);
        
        // Display device info
        updateDeviceInfo();
    }
    
    private void setupListeners() {
        switchAutoRecord.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (hasAllPermissions()) {
                    startRecordingService();
                    updateStatus("Auto-recording enabled");
                } else {
                    switchAutoRecord.setChecked(false);
                    Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show();
                }
            } else {
                stopRecordingService();
                updateStatus("Auto-recording disabled");
            }
        });
        
        btnCheckPermissions.setOnClickListener(v -> checkAndRequestPermissions());
        
        btnEnableAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivityForResult(intent, ACCESSIBILITY_REQUEST_CODE);
            Toast.makeText(this, "Enable 'Call Recorder' in Accessibility Services", Toast.LENGTH_LONG).show();
        });
        
        btnRequestMediaProjection.setOnClickListener(v -> requestMediaProjection());
        
        btnApplyRootFixes.setOnClickListener(v -> applyRootFixes());
        
        // REDMI 10C: Show simplified setup if detected
        if (RedmiOptimizations.isRedmiDevice()) {
            showRedmiQuickSetup();
        }
    }
    
    private void requestMediaProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), MEDIA_PROJECTION_REQUEST_CODE);
        } else {
            Toast.makeText(this, "MediaProjection requires Android 5.0+", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void applyRootFixes() {
        if (RootAudioRecorder.isRooted()) {
            new Thread(() -> {
                RootAudioRecorder rootRecorder = new RootAudioRecorder(this);
                boolean success = rootRecorder.enableSystemCallRecording();
                
                ManufacturerWorkarounds workarounds = new ManufacturerWorkarounds(this);
                workarounds.applyWorkarounds();
                
                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "Root fixes applied! Reboot recommended.", Toast.LENGTH_LONG).show();
                        updateStatus("Root access enabled - Perfect recording quality");
                    } else {
                        Toast.makeText(this, "Root fixes failed. Check logs.", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        } else {
            Toast.makeText(this, "Root access not available", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateDeviceInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        info.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        
        // REDMI 10C DETECTION
        if (RedmiOptimizations.isRedmiDevice()) {
            info.append("✅ REDMI DEVICE - PERFECT FOR RECORDING!\n");
            info.append("MIUI Version: ").append(RedmiOptimizations.getMIUIVersion()).append("\n");
            info.append("⭐ Native call recording supported\n");
        }
        
        if (RootAudioRecorder.isRooted()) {
            info.append("Root: ✓ Available (Perfect quality mode)\n");
        } else {
            info.append("Root: ✗ Not available (Native MIUI recording active)\n");
        }
        
        if (ManufacturerWorkarounds.requiresWorkaround() && !RedmiOptimizations.isRedmiDevice()) {
            info.append("⚠️ This device may have recording restrictions");
        }
        
        tvDeviceInfo.setText(info.toString());
    }
    
    private boolean hasAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean hasPhonePermission = ContextCompat.checkSelfPermission(this, 
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
            boolean hasRecordPermission = ContextCompat.checkSelfPermission(this, 
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            boolean hasStoragePermission = true;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hasStoragePermission = Environment.isExternalStorageManager();
            } else {
                hasStoragePermission = ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            }
            
            boolean hasCallLogPermission = ContextCompat.checkSelfPermission(this, 
                Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
            boolean hasOverlayPermission = Settings.canDrawOverlays(this);
            
            return hasPhonePermission && hasRecordPermission && hasStoragePermission 
                && hasCallLogPermission && hasOverlayPermission;
        }
        return true;
    }
    
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE);
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_CALL_LOG);
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissionsNeeded.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        }
        
        // Check for overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
        }
        
        // Check for storage permission on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        }
        
        updatePermissionStatus();
    }
    
    private void updatePermissionStatus() {
        if (hasAllPermissions()) {
            tvStatus.setText("Status: All permissions granted ✓");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvStatus.setText("Status: Missing permissions ✗");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            updatePermissionStatus();
        }
    }
    
    private void startRecordingService() {
        Intent serviceIntent = new Intent(this, CallRecordingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
    
    private void stopRecordingService() {
        Intent serviceIntent = new Intent(this, CallRecordingService.class);
        stopService(serviceIntent);
    }
    
    private void updateStatus(String message) {
        tvStatus.setText("Status: " + message);
    }
    
    private void loadRecordings() {
        File recordingsDir = new File(getExternalFilesDir(null), "CallRecordings");
        if (recordingsDir.exists()) {
            File[] files = recordingsDir.listFiles();
            if (files != null) {
                List<File> recordings = Arrays.asList(files);
                adapter.updateRecordings(recordings);
            }
        }
    }
    
    private void showRedmiQuickSetup() {
        RedmiOptimizations redmiOpt = new RedmiOptimizations(this);
        String instructions = redmiOpt.getRedmiSetupInstructions();
        
        // Show setup dialog for Redmi users
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🎉 Redmi 10C Detected!")
            .setMessage(instructions)
            .setPositiveButton("Apply Redmi Optimizations", (dialog, which) -> {
                new Thread(() -> {
                    boolean success = redmiOpt.applyRedmiOptimizations();
                    runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(this, "✅ Redmi optimizations applied! Ready to record.", Toast.LENGTH_LONG).show();
                            updateStatus("Redmi 10C optimized - Perfect quality enabled!");
                        } else {
                            Toast.makeText(this, "Optimizations applied. Grant permissions if needed.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
            })
            .setNegativeButton("Skip", null)
            .setCancelable(true)
            .show();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                    CallAudioAccessibilityService.setMediaProjection(mediaProjection);
                    Toast.makeText(this, "MediaProjection enabled! Audio capture ready.", Toast.LENGTH_SHORT).show();
                    updateStatus("Enhanced recording enabled (MediaProjection)");
                }
            } else {
                Toast.makeText(this, "MediaProjection denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
        loadRecordings();
    }
}
