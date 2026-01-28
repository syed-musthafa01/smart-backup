package com.example.gptbackup.ai;

import android.content.Context;

import com.example.gptbackup.model.FileModel;

public class PriorityFusionEngine {

    private static final float ML_WEIGHT = 0.6f;
    private static final float BEHAVIOR_WEIGHT = 0.3f;
    private static final float SURVEY_WEIGHT = 0.1f;

    private final BehaviorTracker behaviorTracker;
    private final UserPreferenceManager preferenceManager;

    public PriorityFusionEngine(Context context) {
        behaviorTracker = new BehaviorTracker(context);
        preferenceManager = new UserPreferenceManager(context);
    }

    public int calculateFinalPriority(FileModel file, float mlScore) {

        float behaviorScore =
                behaviorTracker.getBehaviorScore(file.getPath());

        float surveyScore =
                preferenceManager.getCategoryPreference(file.getType());


        float decay =
                preferenceManager.getSurveyDecayFactor();

        float finalScore =
                (mlScore * ML_WEIGHT)
                        + (behaviorScore * BEHAVIOR_WEIGHT)
                        + (surveyScore * decay * SURVEY_WEIGHT);

        if (finalScore >= 70) return 2;
        if (finalScore >= 40) return 1;
        return 0;
    }
}
