package com.datapreprocessor.toolwindow;

import com.datapreprocessor.engine.DataChartFactory;
import com.datapreprocessor.model.ColumnProfile;
import com.datapreprocessor.model.ColumnProfile.DataType;
import com.datapreprocessor.model.DataSet;
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

    // Parallel to columnListModel — only numeric profiles, in the same order
    private final List<ColumnProfile> numericProfiles = new ArrayList<>();

    // Snapshot of the last dataset passed to onDataSetLoaded — used by renderChart()
    // for histogram raw-value access. Must NOT use a coordinator supplier because
    // onApplied() passes a cleaned copy that is never written back to currentDataSet.
    private DataSet currentDs;

    VisualisationPanel() {
        ButtonGroup toggle = new ButtonGroup();
        toggle.add(histBtn);
        toggle.add(boxBtn);

        histBtn.addActionListener(e -> renderChart());
        boxBtn.addActionListener(e  -> renderChart());

        columnList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        columnList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) renderChart();
        });
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

        JPanel right = new JPanel(new BorderLayout(0, 4));
        right.add(toggleBar,      BorderLayout.NORTH);
        right.add(chartContainer, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(160);
        split.setResizeWeight(0.0);

        showPlaceholder();
        return split;
    }

    /** Called by the coordinator after a file load, reload, or pipeline Apply. */
    void onDataSetLoaded(DataSet ds, List<ColumnProfile> profiles) {
        currentDs = ds;
        columnListModel.clear();
        numericProfiles.clear();

        for (ColumnProfile p : profiles) {
            if (p.getDataType() == DataType.NUMERIC) {
                columnListModel.addElement(p.getName());
                numericProfiles.add(p);
            }
        }

        if (!numericProfiles.isEmpty()) {
            columnList.setSelectedIndex(0);
            // The list-selection listener fires renderChart() automatically
        } else {
            showPlaceholder();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void renderChart() {
        int idx = columnList.getSelectedIndex();
        if (idx < 0 || idx >= numericProfiles.size()) { showPlaceholder(); return; }

        ColumnProfile profile    = numericProfiles.get(idx);
        boolean       histogram  = histBtn.isSelected();

        // Show a placeholder immediately so the panel isn't blank during computation.
        showLoading();

        // parseDoubles() is O(n) and HistogramDataset.addSeries() sorts the data —
        // both must run off the EDT to avoid freezing the UI on large datasets.
        new SwingWorker<JFreeChart, Void>() {
            @Override protected JFreeChart doInBackground() {
                if (histogram) {
                    List<Double> values = parseDoubles(currentDs.getColumn(profile.getName()));
                    return DataChartFactory.createHistogram(profile.getName(), values, 20);
                }
                // Box plot uses pre-computed ColumnProfile stats — still off EDT for consistency
                return DataChartFactory.createBoxPlot(profile.getName(), profile);
            }
            @Override protected void done() {
                try {
                    ChartPanel cp = new ChartPanel(get());
                    cp.setMouseWheelEnabled(true);
                    chartContainer.removeAll();
                    chartContainer.add(cp, BorderLayout.CENTER);
                    chartContainer.revalidate();
                    chartContainer.repaint();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void showLoading() {
        chartContainer.removeAll();
        JLabel msg = new JLabel("Loading chart…", SwingConstants.CENTER);
        msg.setForeground(Color.GRAY);
        chartContainer.add(msg, BorderLayout.CENTER);
        chartContainer.revalidate();
        chartContainer.repaint();
    }

    private void showPlaceholder() {
        chartContainer.removeAll();
        JLabel msg = new JLabel("Load a dataset to see column charts.", SwingConstants.CENTER);
        msg.setForeground(Color.GRAY);
        chartContainer.add(msg, BorderLayout.CENTER);
        chartContainer.revalidate();
        chartContainer.repaint();
    }

    private List<Double> parseDoubles(List<String> raw) {
        List<Double> out = new ArrayList<>();
        for (String v : raw) {
            if (v == null || v.isBlank()) continue;
            try { out.add(Double.parseDouble(v.trim())); }
            catch (NumberFormatException ignored) {}
        }
        return out;
    }
}