package com.example.gptbackup.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.gptbackup.db.BackupDatabase;
import com.example.gptbackup.db.BackupFileDao;
import com.example.gptbackup.db.BackupFileEntity;
import com.example.gptbackup.db.BackupQueueDao;
import com.example.gptbackup.db.BackupQueueEntity;
import com.example.gptbackup.model.FileModel;

import java.util.List;

public class BackupQueueManager {

    private static final String TAG = "BackupQueueManager";
    private static final String PREF_NAME = "backup_queue_prefs";
    private static final String KEY_LAST_BACKUP = "last_backup_timestamp";

    private final BackupQueueDao queueDao;
    private final BackupFileDao fileDao;
    private final SharedPreferences prefs;

    public BackupQueueManager(Context context) {
        BackupDatabase db = BackupDatabase.getInstance(context);
        this.queueDao = db.backupQueueDao();
        this.fileDao = db.backupFileDao();
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Enqueues a file if it hasn't been uploaded before and is not already in the queue.
     */
    public void enqueueFile(FileModel file) {
        String path = file.getPath();
        if (path == null || path.isEmpty()) {
            // Cannot reliably track without a path
            return;
        }

        // 1. Check if it's already uploaded (deduplication)
        BackupFileEntity existing = fileDao.getByPath(path);
        if (existing != null && "BACKED_UP".equals(existing.backupStatus)) {
            // Check if modified since last backup
            if (existing.lastUploadedTime >= file.getLastModified() && existing.uploadedFileSize == file.getSize()) {
                Log.d(TAG, "File already uploaded and unchanged, skipping: " + path);
                return;
            }
        }

        // 2. Check if it's already in the queue
        BackupQueueEntity queued = queueDao.getByPath(path);
        if (queued != null) {
            Log.d(TAG, "File already in queue, skipping: " + path);
            return;
        }

        // 3. Insert into queue
        BackupQueueEntity newEntity = new BackupQueueEntity(
                path,
                file.getPriority(),
                "PENDING",
                System.currentTimeMillis()
        );
        queueDao.insertOrIgnore(newEntity);
        Log.d(TAG, "Enqueued file: " + path);
    }

    public List<BackupQueueEntity> getPendingFiles(int limit) {
        return queueDao.getPendingFiles(limit);
    }

    public void updateStatus(int id, String status) {
        queueDao.updateStatus(id, status);
    }

    public void recordSuccess(BackupQueueEntity entity, FileModel file, String driveId) {
        // 1. Mark as completed and remove from queue
        // We delete from queue on success to keep queue small
        queueDao.deleteById(entity.id);

        // 2. Add to backup records
        BackupFileEntity record = new BackupFileEntity();
        record.path = entity.path;
        record.driveFileId = driveId;
        record.lastUploadedTime = file.getLastModified() > 0 ? file.getLastModified() : System.currentTimeMillis();
        record.uploadedFileSize = file.getSize();
        record.priority = entity.priority;
        record.backupStatus = "BACKED_UP";
        fileDao.insertOrUpdate(record);
        
        Log.d(TAG, "Backup recorded successfully for: " + entity.path);
    }

    public void recordFailure(BackupQueueEntity entity, String errorMessage) {
        entity.retryCount++;
        entity.status = "FAILED";
        entity.errorMessage = errorMessage;
        queueDao.update(entity);
        Log.e(TAG, "Backup failed for: " + entity.path + " - " + errorMessage);
    }

    public void resetUploadingToPending() {
        queueDao.resetUploadingToPending();
    }

    public long getLastBackupTimestamp() {
        return prefs.getLong(KEY_LAST_BACKUP, 0);
    }

    public void setLastBackupTimestamp(long timestamp) {
        prefs.edit().putLong(KEY_LAST_BACKUP, timestamp).apply();
    }
}
