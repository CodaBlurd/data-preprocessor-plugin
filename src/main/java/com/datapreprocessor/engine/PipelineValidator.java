package com.datapreprocessor.engine;

import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;

import java.util.ArrayList;
import java.util.List;

public class PipelineValidator {

    public List<String> validateColumns(List<PreprocessingStep> steps, List<String> columnNames) {
        List<String> warnings = new ArrayList<>();

        for (PreprocessingStep step : steps) {
            String column = step.column();
            if (column == null || column.isBlank()) continue;

            if (!columnNames.contains(column)) {
                warnings.add("Column not found in current dataset: " + column
                        + " (" + step.operation() + ")");
            }
        }

        return warnings;
    }
}
