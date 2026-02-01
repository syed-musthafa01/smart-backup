package com.example.gptbackup.db;

import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupRepository {

    private final BackupFileDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BackupRepository(Context context) {
        dao = BackupDatabase.getInstance(context).backupFileDao();
    }

    public BackupFileEntity getSync(String path) {
        return dao.getByPath(path);
    }

    public void saveAsync(BackupFileEntity entity) {
        executor.execute(() -> dao.insertOrUpdate(entity));
    }
}
