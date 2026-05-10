package com.datapreprocessor.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class DataPreprocessorConfigurable implements Configurable {

    private JPanel panel;
    private JSpinner previewRowLimitSpinner;
    private ComboBox<String> normalizationCombo;
    private JTextField trainRatioField;

    @Override
    public @Nls String getDisplayName() {
        return "Data Preprocessor";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        previewRowLimitSpinner = new JSpinner(
                new SpinnerNumberModel(100, 1, 10_000, 10)
        );

        normalizationCombo = new ComboBox<>(new String[]{
                DataPreprocessorSettings.NORMALIZATION_MIN_MAX,
                DataPreprocessorSettings.NORMALIZATION_ZSCORE,
                DataPreprocessorSettings.NORMALIZATION_ROBUST
        });

        trainRatioField = new JTextField(8);

        int row = 0;

        addRow(panel, gbc, row++, "Preview row limit:", previewRowLimitSpinner);
        addRow(panel, gbc, row++, "Default normalization:", normalizationCombo);
        addRow(panel, gbc, row++, "Default train/test ratio:", trainRatioField);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weighty = 1;
        gbc.gridwidth = 2;
        panel.add(Box.createVerticalGlue(), gbc);

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        DataPreprocessorSettings settings = DataPreprocessorSettings.getInstance();

        int previewLimit = (Integer) previewRowLimitSpinner.getValue();
        String normalization = (String) normalizationCombo.getSelectedItem();
        String trainRatioText = trainRatioField.getText().trim();

        if (previewLimit != settings.getPreviewRowLimit()) {
            return true;
        }

        if (!settings.getDefaultNormalizationMethod().equals(normalization)) {
            return true;
        }

        try {
            double ratio = Double.parseDouble(trainRatioText);
            return Double.compare(ratio, settings.getDefaultTrainRatio()) != 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    @Override
    public void apply() throws ConfigurationException {
        int previewLimit = (Integer) previewRowLimitSpinner.getValue();

        String normalization = (String) normalizationCombo.getSelectedItem();
        if (normalization == null) {
            throw new ConfigurationException("Choose a default normalization method.");
        }

        double ratio;
        try {
            ratio = Double.parseDouble(trainRatioField.getText().trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Train/test ratio must be a number.");
        }

        if (ratio <= 0 || ratio >= 1) {
            throw new ConfigurationException("Train/test ratio must be greater than 0 and less than 1.");
        }

        DataPreprocessorSettings settings = DataPreprocessorSettings.getInstance();
        settings.setPreviewRowLimit(previewLimit);
        settings.setDefaultNormalizationMethod(normalization);
        settings.setDefaultTrainRatio(ratio);
    }

    @Override
    public void reset() {
        DataPreprocessorSettings settings = DataPreprocessorSettings.getInstance();

        previewRowLimitSpinner.setValue(settings.getPreviewRowLimit());
        normalizationCombo.setSelectedItem(settings.getDefaultNormalizationMethod());
        trainRatioField.setText(String.valueOf(settings.getDefaultTrainRatio()));
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        previewRowLimitSpinner = null;
        normalizationCombo = null;
        trainRatioField = null;
    }

    private void addRow(JPanel target,
                        GridBagConstraints gbc,
                        int row,
                        String label,
                        JComponent component) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        target.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        target.add(component, gbc);
    }
}
