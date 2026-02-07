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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UploadManager – Industrial Upload Engine
 * • Priority-based upload
 * • Per-file pause / resume / cancel
 * • RecyclerView live updates
 * • Background-safe
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

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final AtomicBoolean globalPaused = new AtomicBoolean(false);
    private final AtomicBoolean globalCanceled = new AtomicBoolean(false);

    private final BackupFileDao backupDao;
    private final List<FileModel> uploadQueue = new ArrayList<>();

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

        // Sort by priority (HIGH → LOW)
        List<FileModel> sorted = new ArrayList<>(files);
        Collections.sort(sorted,
                (a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        uploadQueue.addAll(sorted);
    }

    // ================= START =================

    public void start() {

        globalPaused.set(false);
        globalCanceled.set(false);

        executor.execute(() -> {

            int completed = 0;
            int total = uploadQueue.size();

            for (FileModel file : uploadQueue) {

                if (globalCanceled.get()) break;

                // -------- USER CANCELED BEFORE START --------
                if (file.isCanceledByUser()) {
                    updateState(file, UploadState.CANCELED);
                    completed++;
                    notifyGlobal(completed, total);
                    continue;
                }

                // -------- WAIT FOR PAUSE --------
                while ((globalPaused.get() || file.isPausedByUser())
                        && !globalCanceled.get()
                        && !file.isCanceledByUser()) {
                    sleep(300);
                }

                if (file.isCanceledByUser()) {
                    updateState(file, UploadState.CANCELED);
                    completed++;
                    notifyGlobal(completed, total);
                    continue;
                }

                // -------- DUPLICATE CHECK --------
                BackupFileEntity record =
                        backupDao.getByPath(file.getPath());

                if (record != null) {
                    boolean unchanged =
                            record.lastUploadedTime >= file.getLastModified()
                                    && record.uploadedFileSize == file.getSize();

                    if (unchanged) {
                        updateState(file, UploadState.SKIPPED);
                        completed++;
                        notifyGlobal(completed, total);
                        continue;
                    }
                }

                // -------- START UPLOAD --------
                file.setUploading(true);
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

                file.setUploading(false);

                // -------- FINAL STATE --------
                if (file.isCanceledByUser()) {
                    updateState(file, UploadState.CANCELED);
                } else if (success) {
                    updateState(file, UploadState.COMPLETED);

                    BackupFileEntity entity = new BackupFileEntity();
                    entity.path = file.getPath();
                    entity.driveFileId = file.getDriveFileId();
                    entity.lastUploadedTime = System.currentTimeMillis();
                    entity.uploadedFileSize = file.getSize();
                    entity.priority = file.getPriority();

                    backupDao.insertOrUpdate(entity);
                } else {
                    updateState(file, UploadState.FAILED);
                }

                completed++;
                notifyGlobal(completed, total);
            }

            if (!globalCanceled.get()) {
                mainHandler.post(listener::onCompleted);
            }
        });
    }

    // ================= GLOBAL CONTROLS =================

    public void pause() {
        globalPaused.set(true);
    }

    public void resume() {
        globalPaused.set(false);
    }

    public void cancel() {
        globalCanceled.set(true);
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
