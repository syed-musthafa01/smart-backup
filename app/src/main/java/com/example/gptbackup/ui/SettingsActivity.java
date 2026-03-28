package com.example.gptbackup.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.gptbackup.databinding.ActivitySettingsBinding;
import com.example.gptbackup.preferences.SettingsManager;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SettingsManager settingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        settingsManager = SettingsManager.getInstance(this);

        setupToolbar();
        loadSettings();
        setupListeners();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadSettings() {
        // Backup Behavior
        binding.switchAutoBackup.setChecked(settingsManager.isAutoBackupEnabled());
        binding.switchHighPriorityOnly.setChecked(settingsManager.isBackupHighPriorityOnly());
        binding.switchBackgroundUpload.setChecked(settingsManager.isAllowBackgroundUpload());
        binding.switchShowNotification.setChecked(settingsManager.isShowUploadNotification());

        // File Filtering
        binding.sliderMinPriority.setValue(settingsManager.getMinPriority());
        int limit = settingsManager.getMaxFileLimit();
        if (limit == 100) binding.toggleMaxLimit.check(binding.btnLimit100.getId());
        else if (limit == 500) binding.toggleMaxLimit.check(binding.btnLimit500.getId());
        else binding.toggleMaxLimit.check(binding.btnLimit200.getId());

        binding.switchIncludeImages.setChecked(settingsManager.isIncludeImages());
        binding.switchIncludeDocs.setChecked(settingsManager.isIncludeDocuments());
        binding.switchIncludeVideos.setChecked(settingsManager.isIncludeVideos());

        // Network
        binding.switchWifiOnly.setChecked(settingsManager.isUploadOnWifiOnly());
        binding.switchMobileData.setChecked(settingsManager.isAllowMobileData());
        binding.switchChargingOnly.setChecked(settingsManager.isUploadOnlyWhileCharging());

        // Performance
        binding.switchSmartOptimization.setChecked(settingsManager.isEnableSmartOptimization());
        binding.switchUnlimitedConcurrent.setChecked(settingsManager.isUnlimitedConcurrentUploads());
    }

    private void setupListeners() {
        // Backup Behavior
        binding.switchAutoBackup.setOnCheckedChangeListener((v, checked) -> settingsManager.setAutoBackupEnabled(checked));
        binding.switchHighPriorityOnly.setOnCheckedChangeListener((v, checked) -> settingsManager.setBackupHighPriorityOnly(checked));
        binding.switchBackgroundUpload.setOnCheckedChangeListener((v, checked) -> settingsManager.setAllowBackgroundUpload(checked));
        binding.switchShowNotification.setOnCheckedChangeListener((v, checked) -> settingsManager.setShowUploadNotification(checked));

        // File Filtering
        binding.sliderMinPriority.addOnChangeListener((slider, value, fromUser) -> settingsManager.setMinPriority((int) value));
        binding.toggleMaxLimit.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == binding.btnLimit100.getId()) settingsManager.setMaxFileLimit(100);
                else if (checkedId == binding.btnLimit200.getId()) settingsManager.setMaxFileLimit(200);
                else if (checkedId == binding.btnLimit500.getId()) settingsManager.setMaxFileLimit(500);
            }
        });

        binding.switchIncludeImages.setOnCheckedChangeListener((v, checked) -> settingsManager.setIncludeImages(checked));
        binding.switchIncludeDocs.setOnCheckedChangeListener((v, checked) -> settingsManager.setIncludeDocuments(checked));
        binding.switchIncludeVideos.setOnCheckedChangeListener((v, checked) -> settingsManager.setIncludeVideos(checked));

        // Network
        binding.switchWifiOnly.setOnCheckedChangeListener((v, checked) -> settingsManager.setUploadOnWifiOnly(checked));
        binding.switchMobileData.setOnCheckedChangeListener((v, checked) -> settingsManager.setAllowMobileData(checked));
        binding.switchChargingOnly.setOnCheckedChangeListener((v, checked) -> settingsManager.setUploadOnlyWhileCharging(checked));

        // Performance
        binding.switchSmartOptimization.setOnCheckedChangeListener((v, checked) -> settingsManager.setEnableSmartOptimization(checked));
        binding.switchUnlimitedConcurrent.setOnCheckedChangeListener((v, checked) -> settingsManager.setUnlimitedConcurrentUploads(checked));
    }

    @Override
    protected void onPause() {
        super.onPause();
        com.example.gptbackup.backup.AutoBackupTriggerController.getInstance(this).applySettings();
    }
}
