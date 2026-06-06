package com.datapreprocessor.engine;

import com.datapreprocessor.model.PipelineDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PipelineSerializer {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void save(Path path, List<CodeGenerator.PreprocessingStep> steps) throws IOException {
        PipelineDocument doc = new PipelineDocument();
        doc.steps = new ArrayList<>();

        for (CodeGenerator.PreprocessingStep step : steps) {
            doc.steps.add(new PipelineDocument.StepDto(
                    step.operation().name(),
                    step.column(),
                    step.param()
            ));
        }

        objectMapper.writeValue(path.toFile(), doc);
    }

    public List<CodeGenerator.PreprocessingStep> load(Path path) throws IOException {
        PipelineDocument doc = objectMapper.readValue(path.toFile(), PipelineDocument.class);

        if (doc.schemaVersion != 1) {
            throw new IOException("Unsupported .dpp schema version: " + doc.schemaVersion);
        }
        if (doc.steps == null) {
            throw new IOException("Invalid .dpp file: missing steps array.");
        }

        List<CodeGenerator.PreprocessingStep> result = new ArrayList<>();

        for (PipelineDocument.StepDto dto : doc.steps) {
            CodeGenerator.Operation op;
            try {
                op = CodeGenerator.Operation.valueOf(dto.operation);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new IOException("Unknown pipeline operation: " + dto.operation);
            }

            result.add(new CodeGenerator.PreprocessingStep(op, dto.column, dto.param));
        }
        return result;
    }
}
