package com.example.gptbackup.scanner;

import android.os.FileObserver;
import android.util.Log;

import java.io.File;

public class AutoBackupFileObserver extends FileObserver {

    private static final String TAG = "AutoBackupFileObserver";
    private final String basePath;
    private final FileChangeCallback callback;

    public interface FileChangeCallback {
        void onFileModified(String filePath);
    }

    public AutoBackupFileObserver(String path, FileChangeCallback callback) {
        // Listen for CREATE and MODIFY events
        super(path, FileObserver.CREATE | FileObserver.MODIFY | FileObserver.MOVED_TO);
        this.basePath = path;
        this.callback = callback;
    }

    @Override
    public void onEvent(int event, String path) {
        if (path == null) return;

        int finalEvent = event & FileObserver.ALL_EVENTS;
        if (finalEvent == FileObserver.CREATE || finalEvent == FileObserver.MODIFY || finalEvent == FileObserver.MOVED_TO) {
            String fullPath = new File(basePath, path).getAbsolutePath();
            Log.d(TAG, "File event detected: " + fullPath);
            if (callback != null) {
                callback.onFileModified(fullPath);
            }
        }
    }
}
