package com.datapreprocessor.engine;

import com.datapreprocessor.model.DataSet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads tabular data from CSV, Excel (.xlsx), and JSON files into a {@link DataSet}.
 *
 * <p>Use {@link #load(String)} as the main entry point — it dispatches to the
 * correct loader based on file extension. Individual loaders are available
 * directly for testing or targeted use.</p>
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
     * Load xlsx file
     * @param filePath the path to the xlsx file
     * @return a populated {@link DataSet}
     */
    public DataSet loadXlsx(String filePath) throws IOException {
        DataSet dataSet = new DataSet();
        dataSet.setFilePath(filePath);

        try (InputStream inputStream = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();

            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue());
            }
            dataSet.setHeaders(headers);

            List<List<String>> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue; // skip phantom empty rows

                List<String> rowData = new ArrayList<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    rowData.add(cellToString(cell));
                }
                rows.add(rowData);
            }

            dataSet.setRows(rows);
            return dataSet;
        }
    }

    /**
     * Loads a JSON file from the given path.
     * @param path the path to the JSON file
     * @return a populated {@link DataSet}
     * @throws IOException if the file cannot be read or parsed
     */
    public DataSet loadJson(String path) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, Object>> jsonData = objectMapper.readValue(
                new File(path),
                new TypeReference<>() {
                }
        );

        if (jsonData.isEmpty()) {
            DataSet empty = new DataSet();
            empty.setFilePath(path);
            return empty;
        }


        Set<String> headerSet = new LinkedHashSet<>();
        jsonData.forEach(map -> headerSet.addAll(map.keySet()));
        List<String> headers = new ArrayList<>(headerSet);

        List<List<String>> rows = jsonData
                .stream()
                .map(map -> headers.stream().map(map::get)
                        .map(v -> v == null ? "" : v.toString()).toList())
                .toList();


        DataSet dataSet = new DataSet();
        dataSet.setFilePath(path);
        dataSet.setHeaders(headers);
        dataSet.setRows(rows);
        return dataSet;


    }



    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : formatNumeric(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.NUMERIC
                    ? formatNumeric(cell.getNumericCellValue())
                    : cell.getStringCellValue();
            default      -> "";
        };
    }

    private String formatNumeric(double value) {
        // Avoid "1.0" for whole numbers — show "1" instead
        return value == Math.floor(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    public DataSet load(@NotNull String path) throws IOException {
        File file = new File(path);
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) throw new IOException("File has no extension: " + name);
        String ext = name.substring(dot + 1).toLowerCase();
        switch(ext) {
            case "csv" -> {
                    return loadCsv(path);
            }
            case "xlsx" -> {
                    return loadXlsx(path);
            }

            case "json" -> {
                    return loadJson(path);
            }
        }
        throw new IOException("Unsupported file type: " + ext);
    }
}
