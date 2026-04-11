package com.datapreprocessor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory representation of a loaded tabular dataset (e.g. a CSV file).
 * Rows and headers are stored as plain Strings; type inference is handled
 * separately in {@link com.datapreprocessor.engine.DataCleaner}.
 */
public class DataSet {

    private String filePath;
    private List<String> headers;
    private List<List<String>> rows;

    public DataSet() {
        this.headers = new ArrayList<>();
        this.rows    = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Basic accessors
    // -------------------------------------------------------------------------

    public String getFilePath()                  { return filePath; }
    public void   setFilePath(String filePath)   { this.filePath = filePath; }

    public List<String> getHeaders()             { return headers; }
    public void setHeaders(List<String> headers) { this.headers = headers; }

    public List<List<String>> getRows()          { return rows; }
    public void setRows(List<List<String>> rows) { this.rows = rows; }

    public int getRowCount()    { return rows.size(); }
    public int getColumnCount() { return headers.size(); }

    // -------------------------------------------------------------------------
    // Cell access
    // -------------------------------------------------------------------------

    /** Returns the value at (row, col), or {@code null} if out of bounds. */
    public String getValue(int row, int col) {
        if (row < 0 || row >= rows.size()) return null;
        List<String> r = rows.get(row);
        if (col < 0 || col >= r.size())   return null;
        return r.get(col);
    }

    public void setValue(int row, int col, String value) {
        rows.get(row).set(col, value);
    }

    // -------------------------------------------------------------------------
    // Column access
    // -------------------------------------------------------------------------

    public List<String> getColumn(int colIndex) {
        List<String> col = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            col.add(colIndex < row.size() ? row.get(colIndex) : null);
        }
        return col;
    }

    public List<String> getColumn(String colName) {
        int idx = headers.indexOf(colName);
        return idx >= 0 ? getColumn(idx) : new ArrayList<>();
    }

    public int getColumnIndex(String colName) {
        return headers.indexOf(colName);
    }

    // -------------------------------------------------------------------------
    // Mutation helpers used by DataCleaner
    // -------------------------------------------------------------------------

    /** Removes the row at the given index. */
    public void removeRow(int index) {
        rows.remove(index);
    }

    /** Appends a deep copy of another dataset's rows to this one. */
    public void addRows(List<List<String>> newRows) {
        for (List<String> row : newRows) {
            rows.add(new ArrayList<>(row));
        }
    }

    /**
     * Returns a shallow copy of this dataset (same headers, new row list
     * containing references to the same row lists). Useful for preview.
     */
    public DataSet shallowCopy() {
        DataSet copy = new DataSet();
        copy.setFilePath(this.filePath);
        copy.setHeaders(new ArrayList<>(this.headers));
        List<List<String>> copiedRows = new ArrayList<>(this.rows.size());
        for (List<String> row : this.rows) copiedRows.add(new ArrayList<>(row));
        copy.setRows(copiedRows);
        return copy;
    }
}
