package com.datapreprocessor.engine;

import com.datapreprocessor.engine.CodeGenerator.Operation;
import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineValidatorTest {

    @Test
    void returnsNoWarningsForKnownColumnsAndRowLevelSteps() {
        List<PreprocessingStep> steps = List.of(
                new PreprocessingStep(Operation.FILL_MISSING_MEAN, "age"),
                new PreprocessingStep(Operation.REMOVE_DUPLICATES)
        );

        List<String> warnings = new PipelineValidator().validateColumns(steps, List.of("age", "income"));

        assertTrue(warnings.isEmpty());
    }

    @Test
    void warnsForMissingColumns() {
        List<PreprocessingStep> steps = List.of(
                new PreprocessingStep(Operation.FILL_MISSING_MEAN, "age"),
                new PreprocessingStep(Operation.NORMALIZE_ZSCORE, "missing_col")
        );

        List<String> warnings = new PipelineValidator().validateColumns(steps, List.of("age"));

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("missing_col"));
    }
}
