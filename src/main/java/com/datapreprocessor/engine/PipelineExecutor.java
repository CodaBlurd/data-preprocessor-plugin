package com.datapreprocessor.engine;

import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;
import com.datapreprocessor.model.DataSet;
import com.datapreprocessor.model.RegexRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies preprocessing pipeline steps to a dataset.
 *
 * <p>This class contains no Swing/UI code so the same execution path can be
 * reused by preview Apply, batch processing, and tests.</p>
 */
public class PipelineExecutor {

    public DataSet apply(DataSet input, List<PreprocessingStep> steps) {
        DataSet working = input.shallowCopy();
        DataCleaner cleaner = new DataCleaner();
        List<PreprocessingStep> snapshot = new ArrayList<>(steps);

        for (PreprocessingStep step : snapshot) {
            applyStep(cleaner, working, step);
        }

        return working;
    }

    private void applyStep(DataCleaner cleaner, DataSet working, PreprocessingStep step) {
        String col = step.column();
        switch (step.operation()) {
            case DROP_MISSING_ROWS -> cleaner.dropMissingRows(working);
            case FILL_MISSING_MEAN -> cleaner.fillMissingWithMean(working, col);
            case FILL_MISSING_MEDIAN -> cleaner.fillMissingWithMedian(working, col);
            case FILL_MISSING_MODE -> cleaner.fillMissingWithMode(working, col);
            case FILL_MISSING_CUSTOM -> cleaner.fillMissingWith(working, col, step.param());
            case REMOVE_DUPLICATES -> cleaner.removeDuplicates(working);
            case REMOVE_OUTLIERS_IQR -> cleaner.removeOutliers(working, col);
            case NORMALIZE_MINMAX -> cleaner.normalizeMinMax(working, col);
            case NORMALIZE_ZSCORE -> cleaner.normalizeZScore(working, col);
            case CAST_COLUMN -> cleaner.castColumn(working, col, step.param());
            case TRAIN_TEST_SPLIT -> { /* Code-generation only: no in-memory dataset mutation. */ }
            case LABEL_ENCODE -> cleaner.labelEncode(working, col);
            case ONE_HOT_ENCODE -> cleaner.oneHotEncode(working, col);
            case SORT_COLUMN -> cleaner.sortColumn(working, col, !"descending".equals(step.param()));
            case FILTER_ROWS -> {
                String raw = step.param() != null ? step.param() : "==|";
                int sep = raw.indexOf('|');
                String op = sep > 0 ? raw.substring(0, sep) : "==";
                String val = sep >= 0 ? raw.substring(sep + 1) : "";
                cleaner.filterRows(working, col, op, val);
            }
            case NORMALIZE_ROBUST -> cleaner.normalizeRobustScaler(working, col);
            case REGEX_REPLACE -> {
                RegexRule rule = RegexRuleCodec.decodeFromJson(step.param());
                cleaner.regexReplace(working, col, rule.pattern(), rule.replacement());
            }
        }
    }
}
