package com.datapreprocessor.engine;

import com.datapreprocessor.engine.BatchProcessor.BatchSummary;
import com.datapreprocessor.engine.CodeGenerator.Operation;
import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void processesAllValidFilesAndExportsCleanedCsvs() throws Exception {
        Path first = tempDir.resolve("first.csv");
        Path second = tempDir.resolve("second.csv");
        Files.writeString(first, "name,email\nAda,ada@gmail.com\nGrace,grace@yahoo.com\n");
        Files.writeString(second, "name,email\nAlan,alan@company.com\n");

        String rule = RegexRuleCodec.encodeToJson(new com.datapreprocessor.model.RegexRule("@.*$", "@hidden.test"));
        List<PreprocessingStep> steps = List.of(new PreprocessingStep(Operation.REGEX_REPLACE, "email", rule));

        BatchSummary summary = new BatchProcessor().process(List.of(first, second), steps);

        assertEquals(2, summary.successCount());
        assertEquals(0, summary.skippedCount());
        assertEquals(0, summary.failedCount());
        assertTrue(Files.readString(tempDir.resolve("first_cleaned.csv")).contains("ada@hidden.test"));
        assertTrue(Files.readString(tempDir.resolve("second_cleaned.csv")).contains("alan@hidden.test"));
    }

    @Test
    void skipsFilesThatDoNotHaveRequiredPipelineColumns() throws Exception {
        Path valid = tempDir.resolve("valid.csv");
        Path missingColumn = tempDir.resolve("missing.csv");
        Files.writeString(valid, "name,email\nAda,ada@gmail.com\n");
        Files.writeString(missingColumn, "name,status\nGrace,active\n");

        String rule = RegexRuleCodec.encodeToJson(new com.datapreprocessor.model.RegexRule("@.*$", "@hidden.test"));
        List<PreprocessingStep> steps = List.of(new PreprocessingStep(Operation.REGEX_REPLACE, "email", rule));

        BatchSummary summary = new BatchProcessor().process(List.of(valid, missingColumn), steps);

        assertEquals(1, summary.successCount());
        assertEquals(1, summary.skippedCount());
        assertEquals(0, summary.failedCount());
        assertTrue(Files.exists(tempDir.resolve("valid_cleaned.csv")));
        assertTrue(Files.notExists(tempDir.resolve("missing_cleaned.csv")));
        assertTrue(summary.results().get(1).message().contains("email"));
    }
}
