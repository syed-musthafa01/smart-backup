package com.example.gptbackup.backup;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.gptbackup.db.BackupDatabase;
import com.example.gptbackup.db.BackupFileDao;
import com.example.gptbackup.db.BackupFileEntity;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.model.UploadState;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UploadManager – Phase 2
 * • Priority-based upload
 * • Duplicate & change detection
 * • Pause / Resume / Cancel
 */
public class UploadManager {

    public interface Listener {
        void onFileProgress(FileModel file, int progress);
        void onFileStateChanged(FileModel file, UploadState state);
        void onGlobalProgress(int completed, int total);
        void onCompleted();
    }

    private final Context context;
    private final GoogleSignInAccount account;
    private final Listener listener;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean canceled = new AtomicBoolean(false);

    private final BackupFileDao backupDao;
    private List<FileModel> uploadQueue = new ArrayList<>();

    public UploadManager(
            Context context,
            GoogleSignInAccount account,
            List<FileModel> files,
            Listener listener
    ) {
        this.context = context.getApplicationContext();
        this.account = account;
        this.listener = listener;

        this.backupDao = BackupDatabase
                .getInstance(this.context)
                .backupFileDao();

        // Sort by priority (High → Low)
        List<FileModel> sorted = new ArrayList<>(files);
        Collections.sort(sorted, new Comparator<FileModel>() {
            @Override
            public int compare(FileModel o1, FileModel o2) {
                return Integer.compare(o2.getPriority(), o1.getPriority());
            }
        });
        this.uploadQueue = sorted;
    }

    // ================= START =================

    public void start() {
        paused.set(false);
        canceled.set(false);

        executor.execute(() -> {
            int completed = 0;
            int total = uploadQueue.size();

            for (FileModel file : uploadQueue) {

                if (canceled.get()) break;

                // Pause handling
                while (paused.get() && !canceled.get()) {
                    sleep(300);
                }

                if (canceled.get()) break;

                // ---------- PHASE 2 SMART CHECK ----------
                BackupFileEntity record = backupDao.getByPath(file.getPath());

                boolean shouldUpload = true;

                if (record != null) {
                    boolean unchanged =
                            record.lastUploadedTime >= file.getLastModified()
                                    && record.uploadedFileSize == file.getSize();

                    if (unchanged) {
                        shouldUpload = false;
                    }
                }

                if (!shouldUpload) {
                    updateState(file, UploadState.SKIPPED);
                    completed++;
                    notifyGlobal(completed, total);
                    continue;
                }

                // ---------- UPLOAD ----------
                updateState(file, UploadState.UPLOADING);

                boolean success = DriveRestUploader.uploadSingleFile(
                        context,
                        account,
                        file,
                        progress -> {
                            file.setUploadProgress(progress);
                            mainHandler.post(() ->
                                    listener.onFileProgress(file, progress));
                        }
                );

                if (success) {
                    updateState(file, UploadState.COMPLETED);

                    BackupFileEntity entity = new BackupFileEntity();
                    entity.path = file.getPath();
                    entity.driveFileId = file.getDriveFileId();
                    entity.lastUploadedTime = System.currentTimeMillis();
                    entity.uploadedFileSize = file.getSize();
                    entity.priority = file.getPriority();

                    backupDao.insertOrUpdate(entity);
                    completed++;
                } else {
                    updateState(file, UploadState.FAILED);
                }

                notifyGlobal(completed, total);
            }

            if (!canceled.get()) {
                mainHandler.post(listener::onCompleted);
            }
        });
    }

    // ================= CONTROLS =================

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }

    public void cancel() {
        canceled.set(true);
    }

    // ================= HELPERS =================

    private void updateState(FileModel file, UploadState state) {
        file.setUploadState(state);
        mainHandler.post(() ->
                listener.onFileStateChanged(file, state));
    }

    private void notifyGlobal(int completed, int total) {
        mainHandler.post(() ->
                listener.onGlobalProgress(completed, total));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}
