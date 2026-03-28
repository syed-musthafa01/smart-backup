package com.example.gptbackup.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "backup_files")
public class BackupFileEntity {

    @PrimaryKey
    @NonNull
    public String path;          // local file path (unique)

    public String driveFileId;   // Google Drive ID
    public long lastUploadedTime;
    public long uploadedFileSize;
    public int priority;

    public String backupStatus;  // e.g., BACKED_UP, NOT_BACKED_UP, MISSING
    public String checksum;      // MD5 hash or similar
}
