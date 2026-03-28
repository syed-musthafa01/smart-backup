package com.example.gptbackup.backup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.gptbackup.ai.FilePriorityEngine;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.preferences.SettingsManager;
import com.example.gptbackup.scanner.AutoBackupFileObserver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AutoBackupTriggerController {

    private static final String TAG = "AutoBackupTriggerController";
    private static volatile AutoBackupTriggerController instance;
    private final Context context;
    private final SettingsManager settings;
    private final BackupQueueManager queueManager;

    private AutoBackupFileObserver dcimObserver;
    private AutoBackupFileObserver downloadsObserver;
    private AutoBackupFileObserver documentsObserver;

    private AutoBackupTriggerController(Context context) {
        this.context = context.getApplicationContext();
        this.settings = SettingsManager.getInstance(context);
        this.queueManager = new BackupQueueManager(context);
    }

    public static AutoBackupTriggerController getInstance(Context context) {
        if (instance == null) {
            synchronized (AutoBackupTriggerController.class) {
                if (instance == null) {
                    instance = new AutoBackupTriggerController(context);
                }
            }
        }
        return instance;
    }

    public synchronized void applySettings() {
        if (!settings.isAutoBackupEnabled()) {
            stopAllTriggers();
            return;
        }

        startFileObservers();

        if (settings.isAllowBackgroundUpload()) {
            scheduleWorkManager();
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(AutoBackupWorker.WORK_NAME);
        }
    }

    private void startFileObservers() {
        if (dcimObserver == null) {
            File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            dcimObserver = new AutoBackupFileObserver(dcim.getAbsolutePath(), this::processFileForBackup);
            dcimObserver.startWatching();
        }
        if (downloadsObserver == null) {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            downloadsObserver = new AutoBackupFileObserver(downloads.getAbsolutePath(), this::processFileForBackup);
            downloadsObserver.startWatching();
        }
        if (documentsObserver == null) {
            File docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            documentsObserver = new AutoBackupFileObserver(docs.getAbsolutePath(), this::processFileForBackup);
            documentsObserver.startWatching();
        }
        Log.d(TAG, "FileObservers started");
    }

    private void scheduleWorkManager() {
        Constraints.Builder constraintsBuilder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);

        if (settings.isEnableSmartOptimization()) {
            constraintsBuilder.setRequiresBatteryNotLow(true);
        }

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                AutoBackupWorker.class,
                6, TimeUnit.HOURS)
                .setConstraints(constraintsBuilder.build())
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AutoBackupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
        );

        // Also trigger an immediate OneTimeWorkRequest to begin retroactive scan right now
        OneTimeWorkRequest instantRequest = new OneTimeWorkRequest.Builder(AutoBackupWorker.class)
                .setConstraints(constraintsBuilder.build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                AutoBackupWorker.WORK_NAME + "_instant",
                ExistingWorkPolicy.REPLACE,
                instantRequest
        );

        Log.d(TAG, "WorkManager background backup scheduled and instant sweep started");
    }

    public synchronized void stopAllTriggers() {
        if (dcimObserver != null) { dcimObserver.stopWatching(); dcimObserver = null; }
        if (downloadsObserver != null) { downloadsObserver.stopWatching(); downloadsObserver = null; }
        if (documentsObserver != null) { documentsObserver.stopWatching(); documentsObserver = null; }
        
        WorkManager.getInstance(context).cancelUniqueWork(AutoBackupWorker.WORK_NAME);
        Log.d(TAG, "All triggers stopped");
    }

    private void processFileForBackup(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || file.isDirectory()) return;

        // Give it a short delay for file write to complete (debounce)
        new Thread(() -> {
            try {
                Thread.sleep(1500); // 1.5s delay to assure writes
                if (!file.exists() || file.length() == 0) return;

                FileModel model = new FileModel(file.getName(), filePath, file.length(), determineMimeType(file));
                model.setLastModified(file.lastModified());
                model.setType(determineMimeType(file));

                List<FileModel> temp = new ArrayList<>();
                temp.add(model);

                // Use existing Priority Engine
                FilePriorityEngine engine = new FilePriorityEngine(context);
                engine.assignPriorities(temp, context);

                queueManager.enqueueFile(model);

                // Trigger execution immediately so the new photo actually uploads!
                if (settings.isAllowBackgroundUpload()) {
                    OneTimeWorkRequest instantPhotoRequest = new OneTimeWorkRequest.Builder(AutoBackupWorker.class).build();
                    WorkManager.getInstance(context).enqueueUniqueWork(
                            AutoBackupWorker.WORK_NAME + "_instant_photo",
                            ExistingWorkPolicy.REPLACE,
                            instantPhotoRequest
                    );
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed processFileForBackup", e);
            }
        }).start();
    }

    private String determineMimeType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif")) return "image";
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")) return "video";
        if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".aac")) return "audio";
        return "document"; // default
    }
}
