package com.callrecorder;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

/**
 * Centralized helper for resolving the shared "Internal storage/Recordings" folder
 * and making sure every recorder writes files using a consistent extension.
 */
public final class RecordingStorageManager {

    private static final String TAG = "RecordingStorage";
    private static final String APP_FOLDER_NAME = "Call Recorder";

    private RecordingStorageManager() {
        // Utility
    }

    /**
     * Returns (and creates if needed) the public folder that maps to
     * Internal storage/Recordings/Call Recorder on the device.
     */
    public static File getRecordingDirectory(Context context) {
        try {
            File baseDir;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS);
            } else {
                baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            }

            if (baseDir == null) {
                baseDir = Environment.getExternalStorageDirectory();
            }

            File recordingsDir = new File(baseDir, APP_FOLDER_NAME);
            if (!recordingsDir.exists() && !recordingsDir.mkdirs()) {
                Log.e(TAG, "Failed to create recordings directory: " + recordingsDir.getAbsolutePath());
            }
            return recordingsDir;
        } catch (Exception e) {
            Log.e(TAG, "Unable to resolve recordings directory", e);
            return context.getExternalFilesDir(null);
        }
    }

    /**
     * Ensures the provided file uses the expected extension (e.g. .m4a / .wav).
     */
    public static File withExtension(File file, String extension) {
        if (file == null) {
            return null;
        }
        if (TextUtils.isEmpty(extension)) {
            return file;
        }
        String sanitizedExtension = extension.startsWith(".") ? extension : "." + extension;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(0, dot);
        }
        return new File(file.getParent(), name + sanitizedExtension);
    }

    /**
     * Notifies the media scanner so the new file appears instantly in gallery/file apps.
     */
    public static void scanFile(Context context, File file) {
        if (context == null || file == null) {
            return;
        }
        MediaScannerConnection.scanFile(
            context,
            new String[]{file.getAbsolutePath()},
            null,
            null
        );
    }
}
