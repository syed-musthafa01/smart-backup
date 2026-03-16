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

    /**
     * AI-Driven selection based on priority and system conditions.
     * High: 70-100
     * Medium: 40-69
     * Low: 0-39
     */
    public List<FileModel> selectFilesForBackup(List<FileModel> allFiles, boolean allowLowPriority) {

        List<FileModel> selected = new ArrayList<>();

        SystemStatusChecker checker = new SystemStatusChecker(context);
        boolean wifi = checker.isWifiConnected();
        boolean batteryOk = checker.isBatteryOkay();

        for (FileModel f : allFiles) {
            int p = f.getPriority();

            // HIGH Priority (70-100): Always selected regardless of specific battery/wifi for this manager
            if (p >= 70) {
                selected.add(f);
            }
            // MEDIUM Priority (40-69): Only if conditions are optimal
            else if (p >= 40) {
                if (wifi && batteryOk) {
                    selected.add(f);
                }
            }
            // LOW Priority (0-39): Only if user explicitly allows it
            else if (allowLowPriority) {
                selected.add(f);
            }
        }

        Toast.makeText(context,
                "Smart Selection: " + selected.size() + " files ready",
                Toast.LENGTH_SHORT).show();

        return selected;
    }
}
