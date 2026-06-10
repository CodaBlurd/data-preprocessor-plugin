package com.datapreprocessor.engine;

import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;
import com.datapreprocessor.model.DataSet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies one preprocessing pipeline to many files and exports cleaned CSVs.
 */
public class BatchProcessor {

    private final DataLoader loader;
    private final DataExporter exporter;
    private final PipelineExecutor executor;
    private final PipelineValidator validator;

    public BatchProcessor() {
        this(new DataLoader(), new DataExporter(), new PipelineExecutor(), new PipelineValidator());
    }

    BatchProcessor(DataLoader loader,
                   DataExporter exporter,
                   PipelineExecutor executor,
                   PipelineValidator validator) {
        this.loader = loader;
        this.exporter = exporter;
        this.executor = executor;
        this.validator = validator;
    }

    public BatchSummary process(List<Path> files, List<PreprocessingStep> steps) {
        List<BatchFileResult> results = new ArrayList<>();
        List<PreprocessingStep> stepSnapshot = new ArrayList<>(steps);

        for (Path file : files) {
            results.add(processOne(file, stepSnapshot));
        }

        return new BatchSummary(results);
    }

    private BatchFileResult processOne(Path file, List<PreprocessingStep> steps) {
        try {
            DataSet input = loader.load(file.toString());
            List<String> warnings = validator.validateColumns(steps, input.getHeaders());
            if (!warnings.isEmpty()) {
                return BatchFileResult.skipped(file, String.join("; ", warnings));
            }

            DataSet cleaned = executor.apply(input, steps);
            String outputPath = DataExporter.cleanedCsvPath(file.toString());
            exporter.exportCsv(cleaned, outputPath);
            return BatchFileResult.success(file, Path.of(outputPath), cleaned.getRowCount());
        } catch (IOException | RuntimeException ex) {
            return BatchFileResult.failed(file, ex.getMessage());
        }
    }

    public record BatchSummary(List<BatchFileResult> results) {
        public int successCount() {
            return (int) results.stream().filter(BatchFileResult::success).count();
        }

        public int skippedCount() {
            return (int) results.stream().filter(BatchFileResult::skipped).count();
        }

        public int failedCount() {
            return (int) results.stream().filter(BatchFileResult::failed).count();
        }

        public int totalCount() {
            return results.size();
        }
    }

    public record BatchFileResult(
            Path source,
            Path output,
            int rowCount,
            Status status,
            String message
    ) {
        public static BatchFileResult success(Path source, Path output, int rowCount) {
            return new BatchFileResult(source, output, rowCount, Status.SUCCESS, "");
        }

        public static BatchFileResult skipped(Path source, String message) {
            return new BatchFileResult(source, null, 0, Status.SKIPPED, message);
        }

        public static BatchFileResult failed(Path source, String message) {
            return new BatchFileResult(source, null, 0, Status.FAILED, message != null ? message : "Unknown error");
        }

        public boolean success() {
            return status == Status.SUCCESS;
        }

        public boolean skipped() {
            return status == Status.SKIPPED;
        }

        public boolean failed() {
            return status == Status.FAILED;
        }
    }

    public enum Status {
        SUCCESS,
        SKIPPED,
        FAILED
    }
}
