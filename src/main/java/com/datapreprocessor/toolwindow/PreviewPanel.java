package com.datapreprocessor.toolwindow;

import com.datapreprocessor.model.DataSet;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URL;

/**
 * Tab 1 — Data Preview.
 *
 * <p>Uses a {@link CardLayout} to switch between two states:</p>
 * <ul>
 *   <li><b>Empty</b> — animated demo GIF shown before any file is loaded.</li>
 *   <li><b>Table</b> — up to {@value #PREVIEW_ROWS} rows of the loaded dataset.</li>
 * </ul>
 *
 * <p>Call {@link #showData(DataSet)} to display data and
 * {@link #showEmpty()} to revert to the placeholder.</p>
 */
class PreviewPanel {

    private static final int    PREVIEW_ROWS = 200;
    private static final String CARD_EMPTY   = "empty";
    private static final String CARD_TABLE   = "table";

    private final JBTable previewTable = new JBTable();
    private final JPanel  card         = new JPanel(new CardLayout());

    PreviewPanel() {
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        previewTable.setFillsViewportHeight(true);

        card.add(buildEmptyPane(),              CARD_EMPTY);
        card.add(new JBScrollPane(previewTable), CARD_TABLE);
    }

    JComponent getContent() {
        return card;
    }

    /**
     * Renders up to {@value #PREVIEW_ROWS} rows from {@code ds} and
     * switches the card to the table view.
     */
    void showData(DataSet ds) {
        int rowCount = Math.min(PREVIEW_ROWS, ds.getRowCount());
        String[] headers = ds.getHeaders().toArray(new String[0]);
        Object[][] data  = new Object[rowCount][ds.getColumnCount()];

        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < ds.getColumnCount(); c++) {
                data[r][c] = ds.getValue(r, c);
            }
        }

        previewTable.setModel(new DefaultTableModel(data, headers) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });

        autoSizeColumns();
        ((CardLayout) card.getLayout()).show(card, CARD_TABLE);
    }

    /** Switches back to the placeholder GIF card. */
    void showEmpty() {
        ((CardLayout) card.getLayout()).show(card, CARD_EMPTY);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private JPanel buildEmptyPane() {
        JPanel emptyPane = new JPanel(new GridBagLayout());
        URL gifUrl = getClass().getResource("/demo_embed.gif");

        if (gifUrl != null) {
            JPanel inner = new JPanel();
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setOpaque(false);

            JLabel gifLabel = new JLabel(new ImageIcon(gifUrl));
            gifLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            inner.add(gifLabel);
            inner.add(Box.createVerticalStrut(10));

            JLabel hint = new JLabel(
                    "Browse a file above, or right-click any .csv / .xlsx / .json "
                    + "→ Open in Data Preprocessor");
            hint.setForeground(JBColor.GRAY);
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);
            inner.add(hint);

            emptyPane.add(inner);
        } else {
            // Fallback when running without the full resource bundle (e.g. unit tests)
            emptyPane.add(new JLabel("Load a CSV, Excel, or JSON file to get started."));
        }

        return emptyPane;
    }

    /**
     * Sizes each column to its widest content, sampling the header and the
     * first 50 rows. Capped at 250 px per column to prevent horizontal sprawl.
     */
    private void autoSizeColumns() {
        for (int c = 0; c < previewTable.getColumnCount(); c++) {
            int width = 60; // floor

            // Measure header
            var headerRenderer = previewTable.getTableHeader().getDefaultRenderer();
            var headerComp = headerRenderer.getTableCellRendererComponent(
                    previewTable, previewTable.getColumnName(c), false, false, -1, c);
            width = Math.max(width, headerComp.getPreferredSize().width + 10);

            // Measure up to 50 data rows
            int sample = Math.min(50, previewTable.getRowCount());
            for (int r = 0; r < sample; r++) {
                var cellComp = previewTable.prepareRenderer(
                        previewTable.getCellRenderer(r, c), r, c);
                width = Math.max(width, cellComp.getPreferredSize().width + 10);
            }

            previewTable.getColumnModel()
                        .getColumn(c)
                        .setPreferredWidth(Math.min(width, 250));
        }
    }
}
