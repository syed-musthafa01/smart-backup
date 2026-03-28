package com.example.gptbackup.backup;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.gptbackup.db.BackupDatabase;
import com.example.gptbackup.db.BackupFileDao;
import com.example.gptbackup.db.BackupFileEntity;
import com.example.gptbackup.model.FileModel;
import com.example.gptbackup.model.ManifestFile;
import com.example.gptbackup.model.ManifestModel;
import com.example.gptbackup.model.UploadState;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UploadManager – Industrial Upload Engine
 */
public class UploadManager {

    private static final String TAG = "UploadManager";

    public interface Listener {
        void onFileProgress(FileModel file, int progress);
        void onFileStateChanged(FileModel file, UploadState state);
        void onGlobalProgress(int completed, int total);
        void onCompleted();
    }

    private final Context context;
    private final GoogleSignInAccount account;
    private final Listener listener;
    private final GoogleDriveSyncManager syncManager;
    private final ManifestModel localManifest;

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
            Listener listener,
            GoogleDriveSyncManager syncManager
    ) {
        this.context = context.getApplicationContext();
        this.account = account;
        this.listener = listener;
        this.syncManager = syncManager;
        this.localManifest = syncManager.getLocalManifest();

        this.backupDao = BackupDatabase
                .getInstance(this.context)
                .backupFileDao();

        // Priority sort (HIGH → LOW)
        List<FileModel> sorted = new ArrayList<>(files);
        Collections.sort(sorted,
                (a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        uploadQueue.addAll(sorted);
    }

    public void start() {
        globalPaused.set(false);
        globalCanceled.set(false);

        int total = uploadQueue.size();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicBoolean updatedManifest = new AtomicBoolean(false);

        for (FileModel file : uploadQueue) {
            executor.execute(() -> {
                if (globalCanceled.get()) return;

                if (file.isCanceledByUser()) {
                    updateState(file, UploadState.CANCELED);
                    notifyDone(completed.incrementAndGet(), total, updatedManifest);
                    return;
                }

                while ((globalPaused.get() || file.isPausedByUser())
                        && !file.isCanceledByUser()
                        && !globalCanceled.get()) {
                    sleep(300);
                }

                if (file.isCanceledByUser() || globalCanceled.get()) {
                    updateState(file, UploadState.CANCELED);
                    notifyDone(completed.incrementAndGet(), total, updatedManifest);
                    return;
                }

                String safePath = buildSafePath(file);

                // MANIFEST DUPLICATE CHECK
                synchronized (localManifest) {
                    boolean isBackedUp = false;
                    for (ManifestFile mf : localManifest.getFiles()) {
                        if (mf.getFilePath().equals(safePath) && "BACKED_UP".equals(mf.getBackupStatus())) {
                            if (mf.getLastModified() >= file.getLastModified()) {
                                isBackedUp = true;
                                break;
                            }
                        }
                    }

                    if (isBackedUp) {
                        updateState(file, UploadState.SKIPPED);
                        notifyDone(completed.incrementAndGet(), total, updatedManifest);
                        return;
                    }
                }

                file.setUploading(true);
                updateState(file, UploadState.UPLOADING);

                DriveRestUploader.UploadHandle handle =
                        DriveRestUploader.uploadToDrive(
                                context,
                                account,
                                file,
                                syncManager.getFolderIds(),
                                progress -> {
                                    file.setUploadProgress(progress);
                                    mainHandler.post(() ->
                                            listener.onFileProgress(file, progress));
                                },
                                globalPaused,
                                globalCanceled
                        );

                file.setUploadHandle(handle);

                while (!file.isCanceledByUser()
                        && !globalCanceled.get()
                        && file.getUploadProgress() < 100 && file.getUploadState() == UploadState.UPLOADING) {
                    sleep(300);
                }

                file.setUploading(false);

                if (file.isCanceledByUser() || globalCanceled.get()) {
                    if (handle != null) handle.cancel();
                    updateState(file, UploadState.CANCELED);
                } else if (file.getUploadProgress() >= 100 || file.getUploadState() == UploadState.COMPLETED) {
                    updateState(file, UploadState.COMPLETED);

                    // UPDATE DB
                    BackupFileEntity entity = new BackupFileEntity();
                    entity.path = safePath;
                    entity.driveFileId = file.getDriveFileId();
                    entity.lastUploadedTime = System.currentTimeMillis();
                    entity.uploadedFileSize = file.getSize();
                    entity.priority = file.getPriority();
                    entity.backupStatus = "BACKED_UP";
                    backupDao.insertOrUpdate(entity);

                    // UPDATE MANIFEST
                    synchronized (localManifest) {
                        ManifestFile mf = new ManifestFile();
                        mf.setFileId(file.getDriveFileId());
                        mf.setFileName(file.getName());
                        mf.setFilePath(safePath);
                        mf.setType(file.getType());
                        mf.setPriority(file.getPriority());
                        mf.setBackupStatus("BACKED_UP");
                        mf.setLastModified(System.currentTimeMillis());
                        
                        localManifest.getFiles().removeIf(m -> m.getFilePath().equals(safePath));
                        localManifest.getFiles().add(mf);
                        updatedManifest.set(true);
                        syncManager.saveLocalManifest(localManifest);
                    }
                } else {
                    updateState(file, UploadState.FAILED);
                }

                notifyDone(completed.incrementAndGet(), total, updatedManifest);
            });
        }
    }

    private String buildSafePath(FileModel file) {
        if (file.getPath() != null && !file.getPath().isEmpty()) {
            return file.getPath();
        }
        if (file.isFromMediaStore() && file.getContentUri() != null) {
            return "mediastore://" + file.getContentUri().toString();
        }
        return "unknown://" + file.getName();
    }

    public void pause() { globalPaused.set(true); }
    public void resume() { globalPaused.set(false); }
    public void cancel() { globalCanceled.set(true); }

    private void updateState(FileModel file, UploadState state) {
        file.setUploadState(state);
        mainHandler.post(() -> listener.onFileStateChanged(file, state));
    }

    private void notifyDone(int completed, int total, AtomicBoolean manifestDirty) {
        mainHandler.post(() -> listener.onGlobalProgress(completed, total));

        if (completed == total && !globalCanceled.get()) {
            // BATCH UPLOAD MANIFEST AT END
            if (manifestDirty.get()) {
                executor.execute(() -> {
                    try {
                        syncManager.updateAndUploadManifest(localManifest);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to upload manifest", e);
                    }
                });
            }
            mainHandler.post(listener::onCompleted);
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}