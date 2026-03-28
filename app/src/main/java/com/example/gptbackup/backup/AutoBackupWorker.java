package com.example.gptbackup.backup;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.gptbackup.ai.FilePriorityEngine;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.preferences.SettingsManager;
import com.example.gptbackup.scanner.FileScanner;
import com.example.gptbackup.scanner.MediaStoreLoader;

import java.util.ArrayList;
import java.util.List;

public class AutoBackupWorker extends Worker {

    public static final String WORK_NAME = "auto_backup_worker";
    private static final String TAG = "AutoBackupWorker";

    private final Context context;

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting periodic auto-backup worker");

        SettingsManager settings = SettingsManager.getInstance(context);
        if (!settings.isAutoBackupEnabled() || !settings.isAllowBackgroundUpload()) {
            Log.d(TAG, "Auto-backup or background upload disabled. Skipping.");
            return Result.success();
        }

        // Actively scan the device for missed or existing files
        Log.d(TAG, "Running retroactive device sweep for missed files...");
        try {
            BackupQueueManager queueManager = new BackupQueueManager(context);
            
            List<FileModel> allFiles = new ArrayList<>();
            allFiles.addAll(MediaStoreLoader.loadImages(context));
            allFiles.addAll(MediaStoreLoader.loadVideos(context));
            allFiles.addAll(new FileScanner().scanAllFiles(true));
            
            FilePriorityEngine engine = new FilePriorityEngine(context);
            BackupExecutor executor = new BackupExecutor(context);
            
            int total = allFiles.size();
            final int BATCH_SIZE = 50;
            
            for (int i = 0; i < total; i += BATCH_SIZE) {
                int end = Math.min(total, i + BATCH_SIZE);
                List<FileModel> batch = allFiles.subList(i, end);
                
                Log.d(TAG, "Processing sub-batch " + i + " to " + (end - 1) + " of " + total);
                
                // assign priorities to this small batch
                engine.assignPriorities(batch, context);
                
                // Enqueue these immediately so they are available for backup
                for (FileModel fm : batch) {
                    queueManager.enqueueFile(fm);
                }
                
                // Start uploading this batch before processing the next batch 
                // This prevents OOM errors and starts backup immediately!
                executor.executeBackup();
            }
            
            // Explicitly clear memory and invite GC to defragment the heap 
            // after the massive ML Kit AI execution to prevent OOM errors during upload!
            allFiles.clear();
            System.gc();
            
            Log.d(TAG, "Retroactive sweep complete.");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Backup worker failed", e);
            return Result.retry();
        }
    }
}
