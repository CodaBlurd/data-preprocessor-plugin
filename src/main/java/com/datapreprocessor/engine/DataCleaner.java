package com.datapreprocessor.engine;

import com.datapreprocessor.model.ColumnProfile;
import com.datapreprocessor.model.ColumnProfile.DataType;
import com.datapreprocessor.model.DataSet;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core data-cleaning and transformation engine.
 *
 * <p>Every public method mutates the supplied {@link DataSet} in-place and
 * returns the same instance so calls can be chained.</p>
 *
 * <p>To keep a clean copy before destructive operations, call
 * {@link DataSet#shallowCopy()} first.</p>
 */
public class DataCleaner {

    // =========================================================================
    // Profiling
    // =========================================================================

    /**
     * Builds a {@link ColumnProfile} for every column in the dataset.
     *
     * @return ordered list of profiles, one per column
     */
    public List<ColumnProfile> profileColumns(DataSet ds) {
        List<ColumnProfile> profiles = new ArrayList<>();
        for (int c = 0; c < ds.getColumnCount(); c++) {
            profiles.add(profileColumn(ds, c));
        }
        return profiles;
    }

    private ColumnProfile profileColumn(DataSet ds, int colIndex) {
        String colName = ds.getHeaders().get(colIndex);
        List<String> values = ds.getColumn(colIndex);

        int total     = values.size();
        int nullCount = 0;
        Map<String, Integer> freq = new LinkedHashMap<>();

        List<Double> numerics = new ArrayList<>();

        for (String v : values) {
            if (v == null || v.isBlank()) {
                nullCount++;
                continue;
            }
            freq.merge(v, 1, Integer::sum);
            tryParseDouble(v).ifPresent(numerics::add);
        }

        int uniqueCount = freq.size();
        String mostCommon = freq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

        DataType dtype = inferType(values);
        ColumnProfile.Builder b = new ColumnProfile.Builder(colName)
                .dataType(dtype)
                .totalCount(total)
                .nullCount(nullCount)
                .uniqueCount(uniqueCount)
                .mostCommon(mostCommon);

        if (!numerics.isEmpty()) {
            double[] arr = numerics.stream().mapToDouble(Double::doubleValue).toArray();
            DescriptiveStatistics stats = new DescriptiveStatistics(arr);
            b.mean(stats.getMean())
             .median(stats.getPercentile(50))
             .stdDev(stats.getStandardDeviation())
             .min(stats.getMin())
             .max(stats.getMax())
             .q1(stats.getPercentile(25))
             .q3(stats.getPercentile(75));
        }

        return b.build();
    }

    // =========================================================================
    // Missing-value handling
    // =========================================================================

    /** Removes any row that contains at least one null / blank cell. */
    public DataSet dropMissingRows(DataSet ds) {
        ds.getRows().removeIf(row ->
            row.stream().anyMatch(v -> v == null || v.isBlank()));
        return ds;
    }

    /** Fills null / blank values in {@code colName} with their column mean. */
    public DataSet fillMissingWithMean(DataSet ds, String colName) {
        double mean = columnMean(ds, colName);
        return fillMissingWith(ds, colName, formatDouble(mean));
    }

    /** Fills null / blank values in {@code colName} with their column median. */
    public DataSet fillMissingWithMedian(DataSet ds, String colName) {
        double median = columnPercentile(ds, colName, 50);
        return fillMissingWith(ds, colName, formatDouble(median));
    }

    /** Fills null / blank values in {@code colName} with the most frequent value. */
    public DataSet fillMissingWithMode(DataSet ds, String colName) {
        String mode = columnMode(ds, colName);
        return fillMissingWith(ds, colName, mode);
    }

    /** Fills null / blank values in {@code colName} with a custom string. */
    public DataSet fillMissingWith(DataSet ds, String colName, String fill) {
        int col = ds.getColumnIndex(colName);
        if (col < 0) return ds;
        for (int r = 0; r < ds.getRowCount(); r++) {
            String v = ds.getValue(r, col);
            if (v == null || v.isBlank()) ds.setValue(r, col, fill);
        }
        return ds;
    }

    // =========================================================================
    // Duplicate removal
    // =========================================================================

    /** Removes all duplicate rows (keeps first occurrence). */
    public DataSet removeDuplicates(DataSet ds) {
        Set<String> seen = new LinkedHashSet<>();
        ds.getRows().removeIf(row -> !seen.add(String.join("\u0000", toStringList(row))));
        return ds;
    }

    // =========================================================================
    // Outlier detection / removal  (IQR fence method)
    // =========================================================================

    /**
     * Removes rows where the value in {@code colName} falls outside the
     * [Q1 - 1.5×IQR, Q3 + 1.5×IQR] fence.
     */
    public DataSet removeOutliers(DataSet ds, String colName) {
        int col = ds.getColumnIndex(colName);
        if (col < 0) return ds;

        double q1    = columnPercentile(ds, colName, 25);
        double q3    = columnPercentile(ds, colName, 75);
        double iqr   = q3 - q1;
        double lower = q1 - 1.5 * iqr;
        double upper = q3 + 1.5 * iqr;

        ds.getRows().removeIf(row -> {
            String v = (col < row.size()) ? row.get(col) : null;
            if (v == null || v.isBlank()) return false;   // leave nulls to missing-value step
            Optional<Double> d = tryParseDouble(v);
            return d.isPresent() && (d.get() < lower || d.get() > upper);
        });
        return ds;
    }

    // =========================================================================
    // Normalization
    // =========================================================================

    /**
     * Min-Max scales {@code colName} to [0, 1].
     * Null / non-numeric cells are left unchanged.
     */
    public DataSet normalizeMinMax(DataSet ds, String colName) {
        int col = ds.getColumnIndex(colName);
        if (col < 0) return ds;

        double min   = columnMin(ds, colName);
        double max   = columnMax(ds, colName);
        double range = max - min;
        if (range == 0) return ds;  // all values equal — nothing to do

        for (int r = 0; r < ds.getRowCount(); r++) {
            String v = ds.getValue(r, col);
            int finalR = r;
            tryParseDouble(v).ifPresent(d ->
                ds.setValue(finalR, col, formatDouble((d - min) / range)));
        }
        return ds;
    }

    /**
     * Z-Score standardises {@code colName} (mean=0, std=1).
     * Null / non-numeric cells are left unchanged.
     */
    public DataSet normalizeZScore(DataSet ds, String colName) {
        int col = ds.getColumnIndex(colName);
        if (col < 0) return ds;

        double mean   = columnMean(ds, colName);
        double stdDev = columnStdDev(ds, colName);
        if (stdDev == 0) return ds;

        for (int r = 0; r < ds.getRowCount(); r++) {
            String v = ds.getValue(r, col);
            int finalR = r;
            tryParseDouble(v).ifPresent(d ->
                ds.setValue(finalR, col, formatDouble((d - mean) / stdDev)));
        }
        return ds;
    }

    // =========================================================================
    // Type casting
    // =========================================================================

    /**
     * Attempts to cast every value in {@code colName} to the target type.
     * Values that cannot be cast are replaced with {@code null}.
     *
     * @param targetType one of: "int", "float", "boolean", "string"
     */
    public DataSet castColumn(DataSet ds, String colName, String targetType) {
        int col = ds.getColumnIndex(colName);
        if (col < 0) return ds;

        for (int r = 0; r < ds.getRowCount(); r++) {
            String v = ds.getValue(r, col);
            if (v == null || v.isBlank()) continue;

            String cast = switch (targetType.toLowerCase()) {
                case "int", "integer" -> {
                    try   { yield String.valueOf((long) Double.parseDouble(v)); }
                    catch (NumberFormatException e) { yield null; }
                }
                case "float", "double" -> {
                    try   { yield formatDouble(Double.parseDouble(v)); }
                    catch (NumberFormatException e) { yield null; }
                }
                case "boolean", "bool" -> {
                    yield switch (v.toLowerCase()) {
                        case "true", "1", "yes", "y"  -> "True";
                        case "false", "0", "no",  "n" -> "False";
                        default -> null;
                    };
                }
                case "string", "str" -> v.trim();
                default -> v;
            };
            ds.setValue(r, col, cast);
        }
        return ds;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private DataType inferType(List<String> values) {
        long nonNull = values.stream().filter(v -> v != null && !v.isBlank()).count();
        if (nonNull == 0) return DataType.UNKNOWN;

        long numericCount = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .filter(v -> tryParseDouble(v).isPresent())
                .count();

        long boolCount = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .filter(v -> v.toLowerCase().matches("true|false|1|0|yes|no|y|n"))
                .count();

        if (numericCount == nonNull)  return DataType.NUMERIC;
        if (boolCount    == nonNull)  return DataType.BOOLEAN;
        return DataType.TEXT;
    }

    private Optional<Double> tryParseDouble(String v) {
        if (v == null || v.isBlank()) return Optional.empty();
        try { return Optional.of(Double.parseDouble(v.trim())); }
        catch (NumberFormatException e) { return Optional.empty(); }
    }

    private double columnMean(DataSet ds, String colName) {
        return columnStats(ds, colName).getMean();
    }

    private double columnStdDev(DataSet ds, String colName) {
        return columnStats(ds, colName).getStandardDeviation();
    }

    private double columnPercentile(DataSet ds, String colName, double p) {
        return columnStats(ds, colName).getPercentile(p);
    }

    private double columnMin(DataSet ds, String colName) {
        return columnStats(ds, colName).getMin();
    }

    private double columnMax(DataSet ds, String colName) {
        return columnStats(ds, colName).getMax();
    }

    private DescriptiveStatistics columnStats(DataSet ds, String colName) {
        double[] arr = ds.getColumn(colName).stream()
                .filter(v -> v != null && !v.isBlank())
                .flatMap(v -> tryParseDouble(v).stream())
                .mapToDouble(Double::doubleValue)
                .toArray();
        return new DescriptiveStatistics(arr);
    }

    private String columnMode(DataSet ds, String colName) {
        return ds.getColumn(colName).stream()
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private List<String> toStringList(List<String> row) {
        return row.stream()
                .map(v -> v == null ? "" : v)
                .collect(Collectors.toList());
    }

    private String formatDouble(double d) {
        // Avoid scientific notation for common values; keep 6 decimal places max
        return Double.isNaN(d) ? "" : String.format("%.6f", d).replaceAll("0+$", "")
                                                                .replaceAll("\\.$", ".0");
    }
}
