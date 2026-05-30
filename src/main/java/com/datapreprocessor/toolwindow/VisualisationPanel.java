package com.datapreprocessor.toolwindow;

import com.datapreprocessor.engine.DataChartFactory;
import com.datapreprocessor.model.ColumnProfile;
import com.datapreprocessor.model.ColumnProfile.DataType;
import com.datapreprocessor.model.DataSet;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class VisualisationPanel {

    private final DefaultListModel<String> columnListModel = new DefaultListModel<>();
    private final JList<String>            columnList      = new JBList<>(columnListModel);
    private final JPanel                   chartContainer  = new JPanel(new BorderLayout());
    private final JToggleButton            histBtn         = new JToggleButton("Histogram", true);
    private final JToggleButton            boxBtn          = new JToggleButton("Box Plot");
    private final JSlider                  binsSlider      = new JSlider(5, 60, 20);
    private final JButton                  resetZoomBtn    = new JButton("Reset Zoom");
    private final JLabel                   statsLabel      = new JLabel(" ");

    // Parallel to columnListModel — only numeric profiles, in the same order
    private final List<ColumnProfile> numericProfiles = new ArrayList<>();

    // Snapshot of the last dataset passed to onDataSetLoaded — used by renderChart()
    // for histogram raw-value access. Must NOT use a coordinator supplier because
    // onApplied() passes a cleaned copy that is never written back to currentDataSet.
    private DataSet currentDs;
    private ChartPanel currentChartPanel;
    private boolean hasLoadedDataset = false;
    private long renderGeneration = 0;

    VisualisationPanel() {
        ButtonGroup toggle = new ButtonGroup();
        toggle.add(histBtn);
        toggle.add(boxBtn);

        binsSlider.setMajorTickSpacing(10);
        binsSlider.setMinorTickSpacing(5);
        binsSlider.setPaintTicks(true);
        binsSlider.setToolTipText("Histogram bin count");
        binsSlider.setPreferredSize(new Dimension(150, binsSlider.getPreferredSize().height));

        histBtn.addActionListener(e -> {
            updateControlStates();
            renderChart();
        });
        boxBtn.addActionListener(e  -> {
            updateControlStates();
            renderChart();
        });
        binsSlider.addChangeListener(e -> {
            if (!binsSlider.getValueIsAdjusting() && histBtn.isSelected()) {
                renderChart();
            }
        });
        resetZoomBtn.addActionListener(e -> {
            if (currentChartPanel != null) {
                currentChartPanel.restoreAutoBounds();
            }
        });

        columnList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        columnList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) renderChart();
        });
        updateControlStates();
    }

    JComponent getContent() {
        // ── Left: column list ─────────────────────────────────────────────────
        JPanel left = new JPanel(new BorderLayout());
        left.setBorder(BorderFactory.createTitledBorder("Numeric columns"));
        left.add(new JBScrollPane(columnList), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(160, 0));

        // ── Right: toggle bar + chart area ────────────────────────────────────
        JPanel toggleBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        toggleBar.add(histBtn);
        toggleBar.add(boxBtn);
        toggleBar.add(new JLabel("Bins"));
        toggleBar.add(binsSlider);
        toggleBar.add(resetZoomBtn);

        statsLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statsLabel.setForeground(JBColor.GRAY);

        JPanel chartArea = new JPanel(new BorderLayout());
        chartArea.add(statsLabel, BorderLayout.NORTH);
        chartArea.add(chartContainer, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(0, 4));
        right.add(toggleBar,      BorderLayout.NORTH);
        right.add(chartArea, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(160);
        split.setResizeWeight(0.0);

        showNoDataset();
        return split;
    }

    /** Called by the coordinator after a file load, reload, or pipeline Apply. */
    void onDataSetLoaded(DataSet ds, List<ColumnProfile> profiles) {
        String previouslySelected = columnList.getSelectedValue();
        hasLoadedDataset = ds != null;
        currentDs = ds;
        renderGeneration++;
        columnListModel.clear();
        numericProfiles.clear();

        for (ColumnProfile p : profiles) {
            if (p.getDataType() == DataType.NUMERIC) {
                columnListModel.addElement(p.getName());
                numericProfiles.add(p);
            }
        }

        if (numericProfiles.isEmpty()) {
            showNoNumericColumns();
            updateControlStates();
            return;
        }

        int restoreIndex = findColumnIndex(previouslySelected);
        if (restoreIndex >= 0) {
            columnList.setSelectedIndex(restoreIndex);
            // The list-selection listener fires renderChart() automatically.
        } else if (previouslySelected != null) {
            columnList.clearSelection();
            showSelectedColumnMissing(previouslySelected);
        } else {
            columnList.setSelectedIndex(0);
            // The list-selection listener fires renderChart() automatically.
        }
        updateControlStates();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void renderChart() {
        int idx = columnList.getSelectedIndex();
        if (idx < 0 || idx >= numericProfiles.size()) {
            if (hasLoadedDataset && numericProfiles.isEmpty()) {
                showNoNumericColumns();
            }
            return;
        }

        DataSet dsSnapshot = currentDs;
        if (dsSnapshot == null) { showNoDataset(); return; }

        ColumnProfile profile   = numericProfiles.get(idx);
        boolean       histogram = histBtn.isSelected();
        int           bins      = binsSlider.getValue();
        long          renderId  = ++renderGeneration;

        updateStatsStrip(profile);
        updateControlStates();

        // Show a placeholder immediately so the panel isn't blank during computation.
        showLoading();

        // parseDoubles() is O(n) and HistogramDataset.addSeries() sorts the data —
        // both must run off the EDT to avoid freezing the UI on large datasets.
        new SwingWorker<JFreeChart, Void>() {
            @Override protected JFreeChart doInBackground() {
                if (histogram) {
                    List<Double> values = parseDoubles(dsSnapshot.getColumn(profile.getName()));
                    return DataChartFactory.createHistogram(profile.getName(), values, bins);
                }
                // Box plot uses pre-computed ColumnProfile stats — still off EDT for consistency
                return DataChartFactory.createBoxPlot(profile.getName(), profile);
            }
            @Override protected void done() {
                if (renderId != renderGeneration) return;

                try {
                    ChartPanel cp = new ChartPanel(get());
                    cp.setMouseWheelEnabled(true);
                    currentChartPanel = cp;
                    chartContainer.removeAll();
                    chartContainer.add(cp, BorderLayout.CENTER);
                    chartContainer.revalidate();
                    chartContainer.repaint();
                    updateControlStates();
                } catch (java.util.concurrent.ExecutionException ex) {
                    // doInBackground threw — show an error message instead of
                    // leaving the panel stuck on "Loading chart…" forever.
                    showError("Could not render chart: " + ex.getCause().getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showNoDataset();
                }
            }
        }.execute();
    }

    private void showLoading() {
        currentChartPanel = null;
        updateControlStates();
        chartContainer.removeAll();
        JLabel msg = new JLabel("Loading chart…", SwingConstants.CENTER);
        msg.setForeground(JBColor.GRAY);
        chartContainer.add(msg, BorderLayout.CENTER);
        chartContainer.revalidate();
        chartContainer.repaint();
    }

    private void showNoDataset() {
        renderGeneration++;
        statsLabel.setText(" ");
        showMessage("Load a dataset to see column charts.", JBColor.GRAY);
    }

    private void showNoNumericColumns() {
        renderGeneration++;
        statsLabel.setText(" ");
        showMessage("No numeric columns found in this dataset.", JBColor.GRAY);
    }

    private void showSelectedColumnMissing(String columnName) {
        renderGeneration++;
        statsLabel.setText(" ");
        showMessage("Column no longer available after Apply: " + columnName, JBColor.GRAY);
    }

    private void showError(String message) {
        showMessage(message, new JBColor(new Color(170, 20, 20), new Color(220, 80, 80)));
    }

    private void showMessage(String message, Color color) {
        currentChartPanel = null;
        updateControlStates();
        chartContainer.removeAll();
        JLabel msg = new JLabel("<html><center>" + htmlEscape(message) + "</center></html>", SwingConstants.CENTER);
        msg.setForeground(color);
        chartContainer.add(msg, BorderLayout.CENTER);
        chartContainer.revalidate();
        chartContainer.repaint();
    }

    private List<Double> parseDoubles(List<String> raw) {
        if (raw == null) return new ArrayList<>();   // Defensive guard for unexpected callers
        List<Double> out = new ArrayList<>();
        for (String v : raw) {
            if (v == null || v.isBlank()) continue;
            try { out.add(Double.parseDouble(v.trim())); }
            catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private int findColumnIndex(String columnName) {
        if (columnName == null) return -1;
        for (int i = 0; i < columnListModel.size(); i++) {
            if (columnName.equals(columnListModel.get(i))) return i;
        }
        return -1;
    }

    private void updateControlStates() {
        binsSlider.setEnabled(histBtn.isSelected() && currentDs != null && !numericProfiles.isEmpty());
        resetZoomBtn.setEnabled(currentChartPanel != null);
    }

    private void updateStatsStrip(ColumnProfile p) {
        statsLabel.setText(String.format(
                "Mean %s   Median %s   Q1 %s   Q3 %s   Min %s   Max %s   Missing %.1f%%",
                formatDouble(p.getMean()),
                formatDouble(p.getMedian()),
                formatDouble(p.getQ1()),
                formatDouble(p.getQ3()),
                formatDouble(p.getMin()),
                formatDouble(p.getMax()),
                p.getNullPercent()));
    }

    private String formatDouble(double d) {
        if (Double.isNaN(d)) return "n/a";
        return String.format("%.4g", d);
    }

    private String htmlEscape(String s) {
        return s == null ? "" : s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
