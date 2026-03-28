package com.example.gptbackup.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "backup_queue", indices = {@Index(value = {"path"}, unique = true)})
public class BackupQueueEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String path;

    public int priority;
    
    // Status can be: PENDING, UPLOADING, FAILED
    public String status;
    
    public int retryCount;
    public long queuedTime;
    public String errorMessage;

    public BackupQueueEntity(@NonNull String path, int priority, String status, long queuedTime) {
        this.path = path;
        this.priority = priority;
        this.status = status;
        this.queuedTime = queuedTime;
        this.retryCount = 0;
    }
}
