package com.datapreprocessor.engine;

import com.datapreprocessor.model.ColumnProfile;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.BoxAndWhiskerItem;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.jfree.data.statistics.HistogramDataset;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Factory for JFreeChart chart instances used by the Data Preprocessor tool window.
 *
 * <p>All chart-creation methods are safe to call off the EDT — they perform no Swing operations.
 * The caller is responsible for wrapping calls in a background thread when needed.</p>
 */
public class DataChartFactory {

    private DataChartFactory() {}   // utility class — no instances

    private static final Color CHART_BG = new Color(43, 43, 43);
    private static final Color PLOT_BG = new Color(60, 60, 60);
    private static final Color GRID = new Color(90, 90, 90);
    private static final Color TEXT = Color.LIGHT_GRAY;
    private static final Color SERIES_BLUE = new Color(93, 162, 232);
    private static final Color SERIES_BLUE_OUTLINE = new Color(55, 120, 185);
    private static final Color MEAN_ORANGE = new Color(255, 165, 0);
    private static final Color MEDIAN_GREEN = new Color(80, 200, 120);

    // ── Histogram ─────────────────────────────────────────────────────────────

    /**
     * Creates a histogram for the given numeric values.
     *
     * <p>Adds vertical marker lines for the mean (orange) and median (green) so the
     * distribution shape is immediately readable without counting bars.</p>
     *
     * @param colName  column name used in title and axis label
     * @param values   pre-parsed, non-null, non-empty list of double values
     * @param bins     number of histogram bins
     */
    public static JFreeChart createHistogram(String colName, List<Double> values, int bins) {
        if (values.isEmpty()) return blankChart(colName + " — no numeric data");

        HistogramDataset dataset = new HistogramDataset();
        double[] arr = values.stream().mapToDouble(Double::doubleValue).toArray();
        dataset.addSeries(colName, arr, bins);

        JFreeChart chart = ChartFactory.createHistogram(
                colName + " — Distribution",
                colName, "Frequency",
                dataset,
                PlotOrientation.VERTICAL,
                false, true, false);

        // ── Theme ─────────────────────────────────────────────────────────────
        chart.setBackgroundPaint(CHART_BG);
        chart.getTitle().setPaint(TEXT);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(PLOT_BG);
        plot.setDomainGridlinePaint(GRID);
        plot.setRangeGridlinePaint(GRID);
        plot.getDomainAxis().setLabelPaint(TEXT);
        plot.getDomainAxis().setTickLabelPaint(TEXT);
        plot.getRangeAxis().setLabelPaint(TEXT);
        plot.getRangeAxis().setTickLabelPaint(TEXT);

        XYBarRenderer renderer = (XYBarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, SERIES_BLUE);
        renderer.setShadowVisible(false);
        renderer.setBarAlignmentFactor(0.5);
        // Light outline makes individual bins easier to distinguish
        renderer.setDrawBarOutline(true);
        renderer.setSeriesOutlinePaint(0, SERIES_BLUE_OUTLINE);
        renderer.setSeriesOutlineStroke(0, new BasicStroke(0.8f));

        // ── Mean / median vertical markers ───────────────────────────────────
        // Computed inline — values list is already available and the sort cost is
        // acceptable because this method always runs off the EDT inside a SwingWorker.
        double mean   = computeMean(values);
        double median = computeMedian(values);

        ValueMarker meanMarker = new ValueMarker(mean);
        meanMarker.setPaint(MEAN_ORANGE);
        meanMarker.setStroke(new BasicStroke(1.8f));

        ValueMarker medianMarker = new ValueMarker(median);
        medianMarker.setPaint(MEDIAN_GREEN);
        medianMarker.setStroke(new BasicStroke(1.8f));

        plot.addDomainMarker(meanMarker);
        plot.addDomainMarker(medianMarker);

        return chart;
    }

    // ── Box plot ──────────────────────────────────────────────────────────────

    /**
     * Creates a box-and-whisker chart from the pre-computed {@link ColumnProfile} statistics.
     *
     * <p>The mean marker is rendered in orange to distinguish it from the median line.
     * Min/max extreme-outlier dot markers are suppressed (passed as {@code null}) because
     * those values are not individual data points but dataset extremes; their information
     * is already conveyed by the whisker ends and by the stats subtitle.</p>
     *
     * @param colName  column name used in title and axis label
     * @param profile  pre-computed column statistics
     */
    public static JFreeChart createBoxPlot(String colName, ColumnProfile profile) {
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();

        // Whisker ends clamped to IQR fences.
        // Passing null for minOutlierValue / maxOutlierValue suppresses the extra
        // "open circle" dot markers that JFreeChart renders at the dataset min/max —
        // those are visually confusing because they look identical to the mean marker.
        BoxAndWhiskerItem item = new BoxAndWhiskerItem(
                profile.getMean(),
                profile.getMedian(),
                profile.getQ1(),
                profile.getQ3(),
                Math.max(profile.getMin(), profile.getLowerFence()),  // lower whisker end
                Math.min(profile.getMax(), profile.getUpperFence()),  // upper whisker end
                null,            // min outlier — suppressed; shown in subtitle instead
                null,            // max outlier — suppressed; shown in subtitle instead
                new ArrayList<>()
        );
        dataset.add(item, colName, "");

        JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(
                colName + " — Box Plot",
                "", colName,
                dataset, false);

        // ── Theme ─────────────────────────────────────────────────────────────
        chart.setBackgroundPaint(CHART_BG);
        chart.getTitle().setPaint(TEXT);

        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(PLOT_BG);
        plot.setRangeGridlinePaint(GRID);
        plot.getRangeAxis().setLabelPaint(TEXT);
        plot.getRangeAxis().setTickLabelPaint(TEXT);
        plot.getDomainAxis().setTickLabelPaint(TEXT);

        BoxAndWhiskerRenderer renderer = (BoxAndWhiskerRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, SERIES_BLUE);                    // box fill
        renderer.setSeriesOutlinePaint(0, Color.WHITE);              // median line, box border, whisker lines
        renderer.setSeriesOutlineStroke(0, new BasicStroke(1.5f));   // slightly thicker so median is readable
        renderer.setFillBox(true);
        // Hide the default mean dot — BoxAndWhiskerRenderer has no setMeanPaint() in
        // JFreeChart 1.5.x, so the dot would be the same blue as the box and hard to
        // distinguish.  We replace it with an orange ValueMarker line instead.
        renderer.setMeanVisible(false);

        // Orange horizontal line at the mean — visually distinct from the blue box
        ValueMarker meanMarker = new ValueMarker(profile.getMean());
        meanMarker.setPaint(MEAN_ORANGE);
        meanMarker.setStroke(new BasicStroke(1.8f));
        plot.addRangeMarker(meanMarker);

        return chart;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Arithmetic mean. Caller guarantees {@code values} is non-empty. */
    private static double computeMean(List<Double> values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    /**
     * Median via sort.  O(n log n) — must only be called off the EDT.
     * Caller guarantees {@code values} is non-empty.
     */
    private static double computeMedian(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return (n % 2 == 0)
                ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0
                : sorted.get(n / 2);
    }

    private static JFreeChart blankChart(String message) {
        // Pass an empty dataset — null causes an NPE inside JFreeChart's renderer
        JFreeChart chart = ChartFactory.createBarChart(message, "", "", new DefaultCategoryDataset());
        chart.setBackgroundPaint(CHART_BG);
        chart.getTitle().setPaint(TEXT);
        return chart;
    }
}
