package com.datapreprocessor.toolwindow;

import com.datapreprocessor.model.ColumnProfile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.List;

/**
 * Tab 2 — Column Profiles.
 *
 * <p>Renders a read-only table of per-column statistics. Call
 * {@link #refresh(List)} whenever a new dataset is loaded or the
 * pipeline is applied.</p>
 */
class ProfilePanel {

    private final JBTable profileTable = new JBTable();

    ProfilePanel() {
        profileTable.setFillsViewportHeight(true);
    }

    JComponent getContent() {
        return new JBScrollPane(profileTable);
    }

    /**
     * Rebuilds the profile table from the given column profiles.
     *
     * @param profiles one entry per column, as returned by
     *                 {@link com.datapreprocessor.engine.DataCleaner#profileColumns}
     */
    void refresh(List<ColumnProfile> profiles) {
        String[] cols = {
                "Column", "Type", "Total", "Nulls", "Null %",
                "Unique", "Mean", "Median", "Std Dev", "Min", "Max", "Most Common"
        };

        Object[][] rows = new Object[profiles.size()][cols.length];
        for (int i = 0; i < profiles.size(); i++) {
            ColumnProfile p = profiles.get(i);
            rows[i] = new Object[]{
                    p.getName(),
                    p.getDataType().name(),
                    p.getTotalCount(),
                    p.getNullCount(),
                    String.format("%.1f%%", p.getNullPercent()),
                    p.getUniqueCount(),
                    fmt(p.getMean()),
                    fmt(p.getMedian()),
                    fmt(p.getStdDev()),
                    fmt(p.getMin()),
                    fmt(p.getMax()),
                    p.getMostCommon()
            };
        }

        profileTable.setModel(new DefaultTableModel(rows, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
    }

    // -------------------------------------------------------------------------

    private String fmt(double d) {
        if (Double.isNaN(d)) return "—";
        return d == Math.floor(d)
                ? String.valueOf((long) d)
                : String.format("%.4f", d);
    }
}
