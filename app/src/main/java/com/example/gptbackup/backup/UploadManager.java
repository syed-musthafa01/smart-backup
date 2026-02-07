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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UploadManager – Industrial Upload Engine
 * • Parallel uploads
 * • Per-file pause / resume / cancel
 * • Safe UI callbacks
 * • Database sync
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

    // ✅ PARALLEL UPLOADS (3 at a time)
    private final ExecutorService executor =
            Executors.newFixedThreadPool(3);

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

        // Priority sort (HIGH → LOW)
        List<FileModel> sorted = new ArrayList<>(files);
        Collections.sort(sorted,
                (a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        uploadQueue.addAll(sorted);
    }

    // ================= START =================

    public void start() {

        globalPaused.set(false);
        globalCanceled.set(false);

        int total = uploadQueue.size();
        AtomicInteger completed = new AtomicInteger(0);

        for (FileModel file : uploadQueue) {

            executor.execute(() -> {

                if (globalCanceled.get()) return;

                // USER canceled before start
                if (file.isCanceledByUser()) {
                    updateState(file, UploadState.CANCELED);
                    notifyDone(completed.incrementAndGet(), total);
                    return;
                }

                // WAIT FOR PAUSE
                while ((globalPaused.get() || file.isPausedByUser())
                        && !file.isCanceledByUser()
                        && !globalCanceled.get()) {
                    sleep(300);
                }

                if (file.isCanceledByUser() || globalCanceled.get()) {
                    updateState(file, UploadState.CANCELED);
                    notifyDone(completed.incrementAndGet(), total);
                    return;
                }

                // DUPLICATE CHECK
                BackupFileEntity record =
                        backupDao.getByPath(file.getPath());

                if (record != null) {
                    boolean unchanged =
                            record.lastUploadedTime >= file.getLastModified()
                                    && record.uploadedFileSize == file.getSize();

                    if (unchanged) {
                        updateState(file, UploadState.SKIPPED);
                        notifyDone(completed.incrementAndGet(), total);
                        return;
                    }
                }

                // START UPLOAD
                file.setUploading(true);
                updateState(file, UploadState.UPLOADING);

                DriveRestUploader.UploadHandle handle =
                        DriveRestUploader.uploadSingleFile(
                                context,
                                account,
                                file,
                                progress -> {
                                    file.setUploadProgress(progress);
                                    mainHandler.post(() ->
                                            listener.onFileProgress(file, progress));
                                }
                        );

                file.setUploadHandle(handle);

                // WAIT FOR COMPLETION
                while (!file.isCanceledByUser()
                        && !globalCanceled.get()
                        && file.getUploadProgress() < 100) {
                    sleep(300);
                }

                file.setUploading(false);

                // FINAL STATE
                if (file.isCanceledByUser()) {
                    handle.cancel(); // 🔥 REAL CANCEL
                    updateState(file, UploadState.CANCELED);

                } else if (file.getUploadProgress() >= 100) {

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

                notifyDone(completed.incrementAndGet(), total);
            });
        }
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

    private void notifyDone(int completed, int total) {
        mainHandler.post(() ->
                listener.onGlobalProgress(completed, total));

        if (completed == total && !globalCanceled.get()) {
            mainHandler.post(listener::onCompleted);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}
