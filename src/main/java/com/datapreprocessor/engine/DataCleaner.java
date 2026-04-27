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

    /**
     * Label encodes the given column. e.g for a column of values ["a", "b", "a"] it will be encoded as ["0", "1", "0"] 0=a, 1=b, 2=a
     * @param ds the dataset to transform
     * @param colName the column to transform
     * @return the transformed dataset
     */
    public DataSet labelEncode(DataSet ds, String colName) {
        int col = ds.getColumnIndex(colName);
        if (col < 0) return ds;

        // Build stable value → index mapping (insertion order)
        Map<String, String> mapping = new LinkedHashMap<>();
        int nextIndex = 0;
        for (int r = 0; r < ds.getRowCount(); r++) {
            String v = ds.getValue(r, col);
            if (v != null && !v.isBlank() && !mapping.containsKey(v)) {
                mapping.put(v, String.valueOf(nextIndex++));
            }
        }
        for (int r = 0; r < ds.getRowCount(); r++) {
            String v = ds.getValue(r, col);
            if (v != null && !v.isBlank()) ds.setValue(r, col, mapping.get(v));
        }
        return ds;
    }

    /**
     * One-hot encodes {@code colName}, replacing it with one binary column per
     * unique value (e.g. {@code city} → {@code city_Austin}, {@code city_Chicago}, …).
     * Rows with a null / blank value in the original column produce all-zero dummy columns.
     * Column order is stable: dummies are inserted at the position of the original column,
     * sorted alphabetically by unique value.
     *
     * @param ds      the dataset to transform (mutated in place)
     * @param colName the column to expand
     * @return the same {@link DataSet} instance with the column expanded
     */
    public DataSet oneHotEncode(DataSet ds, String colName) {
        int colIdx = ds.getColumnIndex(colName);
        if (colIdx < 0) return ds;

        // Collect all unique values for this column (sorted for stable output)
        List<String> uniques = ds.getColumn(colIdx).stream()
                .filter(v -> v != null && !v.isBlank())
                .distinct().sorted().toList();

        // Build new header list — replace colName with one column per unique value
        List<String> newHeaders = new ArrayList<>(ds.getHeaders());
        newHeaders.remove(colIdx);
        List<String> dummyNames = uniques.stream()
                .map(u -> colName + "_" + u).toList();
        newHeaders.addAll(colIdx, dummyNames);
        ds.setHeaders(newHeaders);

        // Rebuild every row
        for (int r = 0; r < ds.getRowCount(); r++) {
            List<String> row = ds.getRows().get(r);
            String val = row.remove(colIdx);  // removes original column value
            List<String> dummies = uniques.stream()
                    .map(u -> u.equals(val) ? "1" : "0").toList();
            row.addAll(colIdx, dummies);
        }
        return ds;
    }

    // =========================================================================
    // Sorting
    // =========================================================================

    /**
     * Sorts the dataset rows by {@code colName}.
     *
     * <p>If every non-null value in the column parses as a number, a numeric
     * comparator is used; otherwise rows are sorted lexicographically.
     * Null / blank values are placed last regardless of direction.</p>
     *
     * @param ds        dataset to sort (mutated in place)
     * @param colName   column to sort by
     * @param ascending {@code true} for A→Z / 0→9; {@code false} for Z→A / 9→0
     * @return the same {@link DataSet} instance
     */
    public DataSet sortColumn(DataSet ds, String colName, boolean ascending) {
        int col = ds.getColumnIndex(colName);
        if (col < 0) return ds;

        boolean allNumeric = ds.getColumn(col).stream()
                .filter(v -> v != null && !v.isBlank())
                .allMatch(v -> tryParseDouble(v).isPresent());

        Comparator<List<String>> cmp = allNumeric
                ? Comparator.comparingDouble(row -> numericSortKey(row, col))
                : Comparator.<List<String>, String>comparing(row -> stringSortKey(row, col));

        ds.getRows().sort(ascending ? cmp : cmp.reversed());
        return ds;
    }

    private double numericSortKey(List<String> row, int col) {
        String v = col < row.size() ? row.get(col) : null;
        return tryParseDouble(v).orElse(Double.MAX_VALUE);   // nulls last
    }

    private String stringSortKey(List<String> row, int col) {
        String v = col < row.size() ? row.get(col) : null;
        return (v == null || v.isBlank()) ? "￿" : v;   // nulls last
    }

    // =========================================================================
    // Row filtering
    // =========================================================================

    /**
     * Keeps only the rows where the value in {@code colName} satisfies the
     * given condition. Rows with a null / blank value in that column are kept
     * unchanged (use {@link #dropMissingRows} to remove them separately).
     *
     * <p>Supported operators: {@code ==}, {@code !=}, {@code >}, {@code <},
     * {@code >=}, {@code <=}, {@code contains} (case-insensitive substring).</p>
     *
     * <p>Numeric comparisons ({@code >}, {@code <}, {@code >=}, {@code <=})
     * only apply when both the cell value and {@code filterValue} parse as
     * numbers; rows that don't parse are kept rather than silently dropped.</p>
     *
     * @param ds          dataset to filter (mutated in place)
     * @param colName     column to test
     * @param operator    one of {@code ==}, {@code !=}, {@code >}, {@code <},
     *                    {@code >=}, {@code <=}, {@code contains}
     * @param filterValue the value to compare against
     * @return the same {@link DataSet} instance
     */
    public DataSet filterRows(DataSet ds, String colName, String operator, String filterValue) {
        int col = ds.getColumnIndex(colName);
        if (col < 0) return ds;

        ds.getRows().removeIf(row -> {
            String v = col < row.size() ? row.get(col) : null;
            if (v == null || v.isBlank()) return false;   // keep missing cells
            return !matchesFilter(v, operator, filterValue);
        });
        return ds;
    }

    private boolean matchesFilter(String cell, String op, String target) {
        return switch (op) {
            case "==" -> cell.equals(target);
            case "!=" -> !cell.equals(target);
            case "contains" -> cell.toLowerCase().contains(target.toLowerCase());
            case ">", "<", ">=", "<=" -> {
                Optional<Double> cv = tryParseDouble(cell);
                Optional<Double> tv = tryParseDouble(target);
                if (cv.isEmpty() || tv.isEmpty()) yield true; // can't compare → keep row
                double c = cv.get(), t = tv.get();
                yield switch (op) {
                    case ">"  -> c > t;
                    case "<"  -> c < t;
                    case ">=" -> c >= t;
                    case "<=" -> c <= t;
                    default   -> true;
                };
            }
            default -> true;
        };
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
