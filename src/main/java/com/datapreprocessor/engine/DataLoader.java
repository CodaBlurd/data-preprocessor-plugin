package com.datapreprocessor.engine;

import com.datapreprocessor.model.DataSet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads tabular data from CSV files into a {@link DataSet}.
 *
 * <p>Uses Apache Commons CSV so it correctly handles quoted fields,
 * commas inside values, and various line endings.</p>
 */
public class DataLoader {

    /**
     * Loads a CSV file from the given path.
     *
     * @param filePath absolute path to the CSV file
     * @return populated {@link DataSet}
     * @throws IOException if the file cannot be read or parsed
     */
    public DataSet loadCsv(String filePath) throws IOException {
        DataSet dataSet = new DataSet();
        dataSet.setFilePath(filePath);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (Reader reader = new FileReader(filePath, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {

            // Extract headers
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            dataSet.setHeaders(headers);

            // Extract rows
            List<List<String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>(headers.size());
                for (String header : headers) {
                    String value = record.get(header);
                    // Normalise empty strings to null for consistent null-handling
                    row.add((value == null || value.isBlank()) ? null : value);
                }
                rows.add(row);
            }
            dataSet.setRows(rows);
        }

        return dataSet;
    }

    /**
     * Convenience: returns only the first {@code maxRows} rows, useful for
     * preview rendering without loading the entire file.
     */
    public DataSet loadCsvPreview(String filePath, int maxRows) throws IOException {
        DataSet full    = loadCsv(filePath);
        DataSet preview = new DataSet();
        preview.setFilePath(full.getFilePath());
        preview.setHeaders(new ArrayList<>(full.getHeaders()));

        List<List<String>> previewRows = full.getRows()
                .subList(0, Math.min(maxRows, full.getRowCount()));
        preview.setRows(new ArrayList<>(previewRows));
        return preview;
    }
}
