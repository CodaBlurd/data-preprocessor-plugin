package com.datapreprocessor.engine;

import com.datapreprocessor.engine.CodeGenerator.Operation;
import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineSerializerTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripsPipelineSteps() throws Exception {
        List<PreprocessingStep> steps = List.of(
                new PreprocessingStep(Operation.FILL_MISSING_MEAN, "age"),
                new PreprocessingStep(Operation.FILTER_ROWS, "income", ">|50000"),
                new PreprocessingStep(Operation.REMOVE_DUPLICATES)
        );
        Path file = tempDir.resolve("pipeline.dpp");

        PipelineSerializer serializer = new PipelineSerializer();
        serializer.save(file, steps);

        assertEquals(steps, serializer.load(file));
        String json = Files.readString(file);
        assertTrue(json.contains("\"schemaVersion\""));
        assertTrue(json.contains("\"FILL_MISSING_MEAN\""));
    }

    @Test
    void rejectsUnknownOperation() throws Exception {
        Path file = tempDir.resolve("unknown-operation.dpp");
        Files.writeString(file, """
                {
                  "schemaVersion": 1,
                  "steps": [
                    { "operation": "DOES_NOT_EXIST", "column": "age", "param": null }
                  ]
                }
                """);

        IOException ex = assertThrows(IOException.class, () -> new PipelineSerializer().load(file));
        assertTrue(ex.getMessage().contains("Unknown pipeline operation"));
    }

    @Test
    void rejectsMissingStepsArray() throws Exception {
        Path file = tempDir.resolve("missing-steps.dpp");
        Files.writeString(file, """
                {
                  "schemaVersion": 1,
                  "createdBy": "Data Preprocessor"
                }
                """);

        IOException ex = assertThrows(IOException.class, () -> new PipelineSerializer().load(file));
        assertTrue(ex.getMessage().contains("missing steps array"));
    }

    @Test
    void rejectsInvalidJson() throws Exception {
        Path file = tempDir.resolve("invalid-json.dpp");
        Files.writeString(file, "{ not valid json");

        assertThrows(IOException.class, () -> new PipelineSerializer().load(file));
    }
}
