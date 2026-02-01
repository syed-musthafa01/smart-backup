package com.example.gptbackup.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface BackupFileDao {

    @Query("SELECT * FROM backup_files WHERE path = :path LIMIT 1")
    BackupFileEntity getByPath(String path);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(BackupFileEntity entity);
}
