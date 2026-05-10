package com.datapreprocessor.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * Stores user settings for the plugin.
 * Persists them through IntelliJ’s settings system.
 */

@Service(Service.Level.APP)
@State(
        name = "DataPreprocessorSettings",
        storages = @Storage("data-preprocessor.xml")
)
public final class DataPreprocessorSettings implements PersistentStateComponent<DataPreprocessorSettings.SettingsState> {

    public static final String NORMALIZATION_MIN_MAX = "MIN_MAX";
    public static final String NORMALIZATION_ZSCORE = "ZSCORE";
    public static final String NORMALIZATION_ROBUST = "ROBUST";

    private SettingsState state = new SettingsState();

    public static DataPreprocessorSettings getInstance() {
        return ApplicationManager.getApplication().getService(DataPreprocessorSettings.class);
    }

    @Override
    public @NotNull SettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull SettingsState state) {
        this.state = state;
    }

    public int getPreviewRowLimit() {
        return Math.max(1, state.previewRowLimit);
    }

    public void setPreviewRowLimit(int previewRowLimit) {
        state.previewRowLimit = Math.max(1, previewRowLimit);
    }

    public String getDefaultNormalizationMethod() {
        return isValidNormalization(state.defaultNormalizationMethod)
                ? state.defaultNormalizationMethod
                : NORMALIZATION_MIN_MAX;
    }

    public void setDefaultNormalizationMethod(String method) {
        state.defaultNormalizationMethod = isValidNormalization(method)
                ? method
                : NORMALIZATION_MIN_MAX;
    }

    public double getDefaultTrainRatio() {
        return state.defaultTrainRatio > 0 && state.defaultTrainRatio < 1
                ? state.defaultTrainRatio
                : 0.8;
    }

    public void setDefaultTrainRatio(double ratio) {
        state.defaultTrainRatio = ratio > 0 && ratio < 1 ? ratio : 0.8;
    }

    private boolean isValidNormalization(String method) {
        return NORMALIZATION_MIN_MAX.equals(method)
                || NORMALIZATION_ZSCORE.equals(method)
                || NORMALIZATION_ROBUST.equals(method);
    }

    public static class SettingsState {
        public int previewRowLimit = 100;
        public String defaultNormalizationMethod = NORMALIZATION_MIN_MAX;
        public double defaultTrainRatio = 0.8;
    }
}
