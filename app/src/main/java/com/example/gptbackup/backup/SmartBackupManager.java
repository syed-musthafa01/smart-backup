package com.example.gptbackup.backup;

import android.content.Context;
import android.widget.Toast;

import com.example.gptbackup.model.FileModel;

import java.util.ArrayList;
import java.util.List;

public class SmartBackupManager {

    private final Context context;

    public SmartBackupManager(Context context) {
        this.context = context;
    }

    public List<FileModel> selectFilesForBackup(List<FileModel> allFiles, boolean allowLowPriority) {

        List<FileModel> selected = new ArrayList<>();

        SystemStatusChecker checker = new SystemStatusChecker(context);
        boolean wifi = checker.isWifiConnected();

        boolean batteryOk = checker.isBatteryOkay();


        // HIGH always
        for (FileModel f : allFiles) {
            if (f.getPriority() == 2) selected.add(f);
        }

        // MEDIUM only if good conditions
        if (wifi && batteryOk) {
            for (FileModel f : allFiles) {
                if (f.getPriority() == 1) selected.add(f);
            }
        }

        // LOW only if user allows
        if (allowLowPriority) {
            for (FileModel f : allFiles) {
                if (f.getPriority() == 0) selected.add(f);
            }
        }

        Toast.makeText(context,
                "Backup Selected: " + selected.size() + " files",
                Toast.LENGTH_SHORT).show();

        return selected;
    }
}
