package com.example.gptbackup.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gptbackup.R;
import com.example.gptbackup.ai.UserPreferenceManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * First-run onboarding wizard (5 steps).
 * Collects backup preferences and stores them to UserPreferenceManager.
 * Only shown once — subsequent launches skip it after onboarding_done = true.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int TOTAL_STEPS = 5;
    private int currentStep = 1;

    // Views shared across steps
    private ProgressBar progressBar;
    private TextView txtStepTitle, txtStepSubtitle;
    private MaterialButton btnNext;
    private View stepIndicator;

    // Step 1 — File Type Priorities
    private View layoutStep1;
    private ChipGroup chipGroupTypes;

    // Step 2 — Screenshots
    private View layoutStep2;
    private RadioGroup radioGroupScreenshots;

    // Step 3 — Camera
    private View layoutStep3;
    private Switch switchCamera;

    // Step 4 — Documents
    private View layoutStep4;
    private Switch switchDocuments;

    // Step 5 — Frequency
    private View layoutStep5;
    private RadioGroup radioGroupFrequency;

    // Collected values
    private boolean wantPhotos    = true;
    private boolean wantVideos    = true;
    private boolean wantDocuments = true;
    private boolean backupScreenshots = false;
    private boolean cameraHighPriority = true;
    private boolean prioritizeTextDocs = true;
    private String  backupFrequency    = "weekly";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        bindViews();
        showStep(1);

        btnNext.setOnClickListener(v -> {
            collectCurrentStepValues();
            if (currentStep < TOTAL_STEPS) {
                currentStep++;
                showStep(currentStep);
            } else {
                savePreferencesAndFinish();
            }
        });
    }

    private void bindViews() {
        progressBar      = findViewById(R.id.onboardingProgress);
        txtStepTitle     = findViewById(R.id.txtOnboardingTitle);
        txtStepSubtitle  = findViewById(R.id.txtOnboardingSubtitle);
        btnNext          = findViewById(R.id.btnOnboardingNext);

        layoutStep1 = findViewById(R.id.layoutStep1);
        layoutStep2 = findViewById(R.id.layoutStep2);
        layoutStep3 = findViewById(R.id.layoutStep3);
        layoutStep4 = findViewById(R.id.layoutStep4);
        layoutStep5 = findViewById(R.id.layoutStep5);

        chipGroupTypes       = findViewById(R.id.chipGroupFileTypes);
        radioGroupScreenshots= findViewById(R.id.radioGroupScreenshots);
        switchCamera         = findViewById(R.id.switchCamera);
        switchDocuments      = findViewById(R.id.switchDocuments);
        radioGroupFrequency  = findViewById(R.id.radioGroupFrequency);

        if (progressBar != null) progressBar.setMax(TOTAL_STEPS);
    }

    private void showStep(int step) {
        hideAllSteps();
        if (progressBar != null) progressBar.setProgress(step);

        switch (step) {
            case 1:
                if (layoutStep1 != null) layoutStep1.setVisibility(View.VISIBLE);
                setTitle("Which files matter most?", "Select all that apply");
                break;
            case 2:
                if (layoutStep2 != null) layoutStep2.setVisibility(View.VISIBLE);
                setTitle("Should screenshots be backed up?", "Screenshots are usually not important");
                break;
            case 3:
                if (layoutStep3 != null) layoutStep3.setVisibility(View.VISIBLE);
                setTitle("Camera roll priority", "Your own photos are usually high priority");
                break;
            case 4:
                if (layoutStep4 != null) layoutStep4.setVisibility(View.VISIBLE);
                setTitle("Text documents", "Invoices, certificates, and PDFs");
                break;
            case 5:
                if (layoutStep5 != null) layoutStep5.setVisibility(View.VISIBLE);
                setTitle("How often do you backup?", "We'll prioritize files that match your rhythm");
                if (btnNext != null) btnNext.setText("Get Started");
                break;
        }
    }

    private void hideAllSteps() {
        if (layoutStep1 != null) layoutStep1.setVisibility(View.GONE);
        if (layoutStep2 != null) layoutStep2.setVisibility(View.GONE);
        if (layoutStep3 != null) layoutStep3.setVisibility(View.GONE);
        if (layoutStep4 != null) layoutStep4.setVisibility(View.GONE);
        if (layoutStep5 != null) layoutStep5.setVisibility(View.GONE);
    }

    private void setTitle(String title, String subtitle) {
        if (txtStepTitle != null) txtStepTitle.setText(title);
        if (txtStepSubtitle != null) txtStepSubtitle.setText(subtitle);
    }

    private void collectCurrentStepValues() {
        switch (currentStep) {
            case 1:
                // Read chip selections
                if (chipGroupTypes != null) {
                    wantPhotos    = isChipChecked(R.id.chipTypePhotos);
                    wantVideos    = isChipChecked(R.id.chipTypeVideos);
                    wantDocuments = isChipChecked(R.id.chipTypeDocuments);
                }
                break;
            case 2:
                if (radioGroupScreenshots != null) {
                    int checked = radioGroupScreenshots.getCheckedRadioButtonId();
                    backupScreenshots = (checked == R.id.radioScreenshotsAlways);
                }
                break;
            case 3:
                if (switchCamera != null) {
                    cameraHighPriority = switchCamera.isChecked();
                }
                break;
            case 4:
                if (switchDocuments != null) {
                    prioritizeTextDocs = switchDocuments.isChecked();
                }
                break;
            case 5:
                if (radioGroupFrequency != null) {
                    int checked = radioGroupFrequency.getCheckedRadioButtonId();
                    if (checked == R.id.radioFreqDaily)        backupFrequency = "daily";
                    else if (checked == R.id.radioFreqMonthly) backupFrequency = "monthly";
                    else                                       backupFrequency = "weekly";
                }
                break;
        }
    }

    private boolean isChipChecked(int chipId) {
        Chip chip = findViewById(chipId);
        return chip != null && chip.isChecked();
    }

    private void savePreferencesAndFinish() {
        UserPreferenceManager prefs = new UserPreferenceManager(this);

        // Type scores: selected = 80, not selected = 30
        prefs.setPhotoPreference(wantPhotos    ? 80 : 30);
        prefs.setVideoPreference(wantVideos    ? 75 : 30);
        prefs.setDocumentPreference(wantDocuments ? 75 : 30);

        prefs.setBackupScreenshots(backupScreenshots);
        prefs.setCameraHighPriority(cameraHighPriority);
        prefs.setPrioritizeTextDocuments(prioritizeTextDocs);
        prefs.setBackupFrequency(backupFrequency);

        // Mark onboarding done
        prefs.setOnboardingComplete(true);

        // Go to main activity
        Intent intent = new Intent(this, SmartBackupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
