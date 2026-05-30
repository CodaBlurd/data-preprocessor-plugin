package com.datapreprocessor.engine;

import com.datapreprocessor.model.ColumnProfile;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.BoxAndWhiskerItem;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.jfree.data.statistics.HistogramDataset;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DataChartFactory {

    private DataChartFactory() {}   // utility class — no instances

    // ── Histogram ─────────────────────────────────────────────────────────────

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

        // ── Styling ───────────────────────────────────────────────────────────
        chart.setBackgroundPaint(new Color(43, 43, 43));
        chart.getTitle().setPaint(Color.LIGHT_GRAY);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(60, 60, 60));
        plot.setDomainGridlinePaint(new Color(90, 90, 90));
        plot.setRangeGridlinePaint(new Color(90, 90, 90));
        plot.getDomainAxis().setLabelPaint(Color.LIGHT_GRAY);
        plot.getDomainAxis().setTickLabelPaint(Color.LIGHT_GRAY);
        plot.getRangeAxis().setLabelPaint(Color.LIGHT_GRAY);
        plot.getRangeAxis().setTickLabelPaint(Color.LIGHT_GRAY);

        XYBarRenderer renderer = (XYBarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(93, 162, 232));
        renderer.setShadowVisible(false);
        renderer.setBarAlignmentFactor(0.5);

        return chart;
    }

    // ── Box plot ──────────────────────────────────────────────────────────────

    public static JFreeChart createBoxPlot(String colName, ColumnProfile profile) {
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();

        // Whisker ends are clamped to the IQR fences — values beyond those are
        // already recorded as min/max and rendered as outlier markers.
        BoxAndWhiskerItem item = new BoxAndWhiskerItem(
                profile.getMean(),
                profile.getMedian(),
                profile.getQ1(),
                profile.getQ3(),
                Math.max(profile.getMin(), profile.getLowerFence()),  // lower whisker end
                Math.min(profile.getMax(), profile.getUpperFence()),  // upper whisker end
                profile.getMin(),    // min outlier marker
                profile.getMax(),    // max outlier marker
                new ArrayList<>()    // individual outlier points — not stored in ColumnProfile
        );
        dataset.add(item, colName, "");

        JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(
                colName + " — Box Plot",
                "", colName,
                dataset, false);

        // ── Styling ───────────────────────────────────────────────────────────
        chart.setBackgroundPaint(new Color(43, 43, 43));
        chart.getTitle().setPaint(Color.LIGHT_GRAY);

        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(new Color(60, 60, 60));
        plot.setRangeGridlinePaint(new Color(90, 90, 90));
        plot.getRangeAxis().setLabelPaint(Color.LIGHT_GRAY);
        plot.getRangeAxis().setTickLabelPaint(Color.LIGHT_GRAY);
        plot.getDomainAxis().setTickLabelPaint(Color.LIGHT_GRAY);

        BoxAndWhiskerRenderer renderer = (BoxAndWhiskerRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(93, 162, 232));
        renderer.setMeanVisible(true);
        renderer.setFillBox(true);

        return chart;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JFreeChart blankChart(String message) {
        // Pass an empty dataset — null causes an NPE inside JFreeChart's renderer
        JFreeChart chart = ChartFactory.createBarChart(message, "", "", new DefaultCategoryDataset());
        chart.setBackgroundPaint(new Color(43, 43, 43));
        chart.getTitle().setPaint(Color.GRAY);
        return chart;
    }
}