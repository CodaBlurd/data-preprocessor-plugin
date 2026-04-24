package com.datapreprocessor.engine;

import com.datapreprocessor.model.DataSet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes a {@link DataSet} or a Python script to disk.
 */
public class DataExporter {

    /**
     * Writes the dataset to a CSV file at {@code outputPath}.
     * Parent directories are created if they don't exist.
     *
     * @param ds         the dataset to export
     * @param outputPath absolute path for the output file
     * @throws IOException if the file cannot be written
     */
    public void exportCsv(DataSet ds, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        Files.createDirectories(path.getParent());

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(ds.getHeaders().toArray(new String[0]))
                .build();

        try (FileWriter fw = new FileWriter(outputPath, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(fw, format)) {

            for (int r = 0; r < ds.getRowCount(); r++) {
                Object[] rowValues = new Object[ds.getColumnCount()];
                for (int c = 0; c < ds.getColumnCount(); c++) {
                    String v = ds.getValue(r, c);
                    rowValues[c] = (v != null) ? v : "";
                }
                printer.printRecord(rowValues);
            }
        }
    }

    /**
     * Writes {@code code} to a {@code .py} file at {@code outputPath}.
     *
     * @param code       Python source code to write
     * @param outputPath absolute path for the output file
     * @throws IOException if the file cannot be written
     */
    public void exportPythonScript(String code, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, code, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Path helpers
    // -------------------------------------------------------------------------

    /**
     * Derives a cleaned CSV output path from the original file path.
     * Always produces a .csv file regardless of the input format.
     * e.g. {@code /data/employees.xlsx} → {@code /data/employees_cleaned.csv}
     */
    public static String cleanedCsvPath(String originalPath) {
        int dot = originalPath.lastIndexOf('.');
        String stem = dot > 0 ? originalPath.substring(0, dot) : originalPath;
        return stem + "_cleaned.csv";
    }

    /**
     * Derives a Python script path from the original CSV path.
     * e.g. {@code /data/employees.csv} → {@code /data/preprocess_employees.py}
     */
    public static String pythonScriptPath(String originalPath) {
        Path p    = Paths.get(originalPath);
        String name = p.getFileName().toString();
        // strip extension
        int dot = name.lastIndexOf('.');
        String stem = (dot > 0) ? name.substring(0, dot) : name;
        return p.getParent().resolve("preprocess_" + stem + ".py").toString();
    }

    private static String insertSuffix(String path, String suffix) {
        int dot = path.lastIndexOf('.');
        return (dot > 0)
                ? path.substring(0, dot) + suffix + path.substring(dot)
                : path + suffix;
    }
}
