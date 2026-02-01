package com.example.gptbackup.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {BackupFileEntity.class},
        version = 2,            // ⬅️ INCREMENT VERSION
        exportSchema = false
)
public abstract class BackupDatabase extends RoomDatabase {

    private static volatile BackupDatabase INSTANCE;

    public abstract BackupFileDao backupFileDao();

    public static BackupDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (BackupDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    BackupDatabase.class,
                                    "backup_db"
                            )
                            .fallbackToDestructiveMigration() // OK for dev
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
