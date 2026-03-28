package com.example.gptbackup.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BackupQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertOrIgnore(BackupQueueEntity entity);

    @Update
    void update(BackupQueueEntity entity);

    @Query("SELECT * FROM backup_queue WHERE path = :path LIMIT 1")
    BackupQueueEntity getByPath(String path);

    // Fetch pending files ordered by priority descending, then oldest first
    @Query("SELECT * FROM backup_queue WHERE status = 'PENDING' OR status = 'FAILED' ORDER BY priority DESC, queuedTime ASC LIMIT :limit")
    List<BackupQueueEntity> getPendingFiles(int limit);

    @Query("DELETE FROM backup_queue WHERE id = :id")
    void deleteById(int id);
    
    @Query("DELETE FROM backup_queue WHERE path = :path")
    void deleteByPath(String path);
    
    @Query("UPDATE backup_queue SET status = :status WHERE id = :id")
    void updateStatus(int id, String status);

    @Query("UPDATE backup_queue SET status = 'PENDING' WHERE status = 'UPLOADING'")
    void resetUploadingToPending();
}
