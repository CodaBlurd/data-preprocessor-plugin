package com.datapreprocessor.toolwindow;

import com.datapreprocessor.engine.CodeGenerator;
import com.datapreprocessor.engine.DataCleaner;
import com.datapreprocessor.engine.DataExporter;
import com.datapreprocessor.engine.CodeGenerator.Operation;
import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;
import com.datapreprocessor.model.ColumnProfile;
import com.datapreprocessor.model.ColumnProfile.DataType;
import com.datapreprocessor.model.DataSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tab 3 — Clean &amp; Transform.
 *
 * <p>Owns the operation form, pipeline step list, and three action buttons.
 * All cross-panel communication happens through constructor-injected callbacks:</p>
 * <ul>
 *   <li>{@code getDataSet}         — reads the currently loaded {@link DataSet}.</li>
 *   <li>{@code onApplied}          — called after Apply with the cleaned copy;
 *                                    coordinator updates the Preview tab only.</li>
 *   <li>{@code onCodeGenerated}    — called after Generate with the Python source;
 *                                    coordinator pushes it to the Code tab.</li>
 *   <li>{@code getSourcePath}      — returns the source file path for CSV export.</li>
 *   <li>{@code onStatus}           — forwards status messages to the shared status bar.</li>
 *   <li>{@code onStepCountChanged} — fires with the new step count on every add/remove/clear;
 *                                    coordinator uses this to update the tab badge.</li>
 * </ul>
 */
class CleanPanel {

    // ── Dependencies (injected) ───────────────────────────────────────────────
    private final Project            project;
    private final Supplier<DataSet>  getDataSet;
    private final Consumer<DataSet>  onApplied;
    private final Consumer<String>   onCodeGenerated;
    private final Supplier<String>   getSourcePath;
    private final Consumer<String>   onStatus;
    private final Consumer<Integer>  onStepCountChanged;

    // ── Pipeline state ────────────────────────────────────────────────────────
    private final List<PreprocessingStep>  pendingSteps      = new ArrayList<>();
    private final DefaultListModel<String> stepListModel     = new DefaultListModel<>();
    private final List<String>             actualColumnNames = new ArrayList<>();

    // The last cleaned result — retained so Export CSV doesn't require re-running Apply
    private DataSet cleanedDataSet;

    // ── Operation form controls ───────────────────────────────────────────────
    private final ComboBox<String> opSelector = new ComboBox<>(new String[]{
            "Drop rows with any missing value",    // 0
            "Fill missing → Mean",                 // 1
            "Fill missing → Median",               // 2
            "Fill missing → Mode",                 // 3
            "Fill missing → Custom value",         // 4
            "Remove duplicate rows",               // 5
            "Remove outliers (IQR)",               // 6
            "Normalize: Min-Max [0,1]",            // 7
            "Normalize: Z-Score (StandardScaler)", // 8
            "Cast column type…",                   // 9
            "Train / Test split",                  // 10
            "Label encode column",                 // 11
            "One-hot encode column",               // 12
            "Sort column…",                        // 13
            "Filter rows by condition",            // 14
            "Normalize: Robust Scaler"             // 15
    });
    private final ComboBox<String> colSelector       = new ComboBox<>();
    private final JTextField       customValueField   = new JTextField(10);
    private final JTextField       splitRatioField    = new JTextField("0.8");
    private final ComboBox<String> castTypeBox        = new ComboBox<>(
            new String[]{ "int", "float", "boolean", "string" });
    private final ComboBox<String> sortOrderBox       = new ComboBox<>(
            new String[]{ "Ascending (A → Z / 0 → 9)", "Descending (Z → A / 9 → 0)" });
    private final ComboBox<String> filterOperatorBox  = new ComboBox<>(
            new String[]{ "==", "!=", ">", "<", ">=", "<=", "contains" });
    private final JTextField       filterValueField   = new JTextField(10);
    private final JList<String>    stepList           = new JList<>(stepListModel);

    // Kept as fields so they can be enabled / disabled dynamically
    private JButton applyBtn;
    private JButton generateBtn;
    private JButton exportBtn;

    // ── Constructor ───────────────────────────────────────────────────────────

    CleanPanel(Project project,
               Supplier<DataSet>  getDataSet,
               Consumer<DataSet>  onApplied,
               Consumer<String>   onCodeGenerated,
               Supplier<String>   getSourcePath,
               Consumer<String>   onStatus,
               Consumer<Integer>  onStepCountChanged) {
        this.project             = project;
        this.getDataSet          = getDataSet;
        this.onApplied           = onApplied;
        this.onCodeGenerated     = onCodeGenerated;
        this.getSourcePath       = getSourcePath;
        this.onStatus            = onStatus;
        this.onStepCountChanged  = onStepCountChanged;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    JComponent getContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(buildOperationForm(), BorderLayout.NORTH);
        panel.add(buildStepList(),      BorderLayout.CENTER);
        panel.add(buildActionBar(),     BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Rebuilds the column selector with type badges when a new dataset is loaded.
     * Stores bare column names separately so badge text never leaks into step params.
     */
    void onDataSetLoaded(DataSet ds, List<ColumnProfile> profiles) {
        colSelector.removeAllItems();
        actualColumnNames.clear();
        for (int i = 0; i < ds.getHeaders().size(); i++) {
            String header = ds.getHeaders().get(i);
            actualColumnNames.add(header);
            String badge = (profiles != null && i < profiles.size())
                    ? "  [" + typeLabel(profiles.get(i).getDataType()) + "]"
                    : "";
            colSelector.addItem(header + badge);
        }
        cleanedDataSet = null;
        updateButtonStates();
    }

    /** Clears all pipeline steps — called when a new file is loaded. */
    void clearPipeline() {
        pendingSteps.clear();
        stepListModel.clear();
        cleanedDataSet = null;
        updateButtonStates();
        notifyStepCount();
    }

    /** Returns a snapshot of the current pipeline for code generation. */
    List<PreprocessingStep> getSteps() {
        return new ArrayList<>(pendingSteps);
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private JPanel buildOperationForm() {
        JPanel builder = new JPanel(new GridBagLayout());
        builder.setBorder(BorderFactory.createTitledBorder("1.  Choose an operation and add it"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(3, 4);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        // Operation selector
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        builder.add(new JLabel("Operation:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        builder.add(opSelector, gbc);

        // Column selector
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        builder.add(new JLabel("Column:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        builder.add(colSelector, gbc);

        // Optional: custom fill value
        JLabel customLabel = new JLabel("Fill value:");
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        builder.add(customLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        builder.add(customValueField, gbc);

        // Optional: cast target type
        JLabel castLabel = new JLabel("Target type:");
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        builder.add(castLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        builder.add(castTypeBox, gbc);

        // Optional: train/test split ratio
        JLabel splitLabel = new JLabel("Train ratio:");
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        builder.add(splitLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        builder.add(splitRatioField, gbc);

        // Optional: sort order
        JLabel sortLabel = new JLabel("Sort order:");
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        builder.add(sortLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        builder.add(sortOrderBox, gbc);

        // Optional: filter operator
        JLabel filterOpLabel = new JLabel("Condition:");
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0;
        builder.add(filterOpLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        builder.add(filterOperatorBox, gbc);

        // Optional: filter value
        JLabel filterValLabel = new JLabel("Value:");
        gbc.gridx = 0; gbc.gridy = 7; gbc.weightx = 0;
        builder.add(filterValLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        builder.add(filterValueField, gbc);

        // All optional fields hidden by default
        customLabel.setVisible(false);    customValueField.setVisible(false);
        castLabel.setVisible(false);      castTypeBox.setVisible(false);
        splitLabel.setVisible(false);     splitRatioField.setVisible(false);
        sortLabel.setVisible(false);      sortOrderBox.setVisible(false);
        filterOpLabel.setVisible(false);  filterOperatorBox.setVisible(false);
        filterValLabel.setVisible(false); filterValueField.setVisible(false);

        opSelector.addActionListener(e -> {
            int idx = opSelector.getSelectedIndex();
            customLabel.setVisible(idx == 4);     customValueField.setVisible(idx == 4);
            castLabel.setVisible(idx == 9);       castTypeBox.setVisible(idx == 9);
            splitLabel.setVisible(idx == 10);     splitRatioField.setVisible(idx == 10);
            sortLabel.setVisible(idx == 13);      sortOrderBox.setVisible(idx == 13);
            filterOpLabel.setVisible(idx == 14);  filterOperatorBox.setVisible(idx == 14);
            filterValLabel.setVisible(idx == 14); filterValueField.setVisible(idx == 14);
        });

        // Add Step button — full width, below the optional fields
        JButton addBtn = new JButton("➕  Add step to pipeline");
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 2; gbc.weightx = 1;
        builder.add(addBtn, gbc);
        addBtn.addActionListener(e -> addStep());

        return builder;
    }

    private JPanel buildStepList() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("2.  Pipeline steps  (applied top → bottom)"));

        stepList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JBScrollPane(stepList), BorderLayout.CENTER);

        JPanel mgmt = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton moveUpBtn   = new JButton("↑ Up");
        JButton moveDownBtn = new JButton("↓ Down");
        JButton removeBtn   = new JButton("Remove");
        JButton clearBtn    = new JButton("Clear all");

        moveUpBtn.addActionListener(e -> {
            int sel = stepList.getSelectedIndex();
            if (sel > 0) {
                String label = stepListModel.remove(sel);
                stepListModel.add(sel - 1, label);
                PreprocessingStep step = pendingSteps.remove(sel);
                pendingSteps.add(sel - 1, step);
                stepList.setSelectedIndex(sel - 1);
            }
        });

        moveDownBtn.addActionListener(e -> {
            int sel = stepList.getSelectedIndex();
            if (sel >= 0 && sel < stepListModel.size() - 1) {
                String label = stepListModel.remove(sel);
                stepListModel.add(sel + 1, label);
                PreprocessingStep step = pendingSteps.remove(sel);
                pendingSteps.add(sel + 1, step);
                stepList.setSelectedIndex(sel + 1);
            }
        });

        removeBtn.addActionListener(e -> {
            int sel = stepList.getSelectedIndex();
            if (sel >= 0) {
                stepListModel.remove(sel);
                pendingSteps.remove(sel);
                updateButtonStates();
                notifyStepCount();
            }
        });

        clearBtn.addActionListener(e -> clearPipeline());

        mgmt.add(moveUpBtn);
        mgmt.add(moveDownBtn);
        mgmt.add(removeBtn);
        mgmt.add(clearBtn);
        panel.add(mgmt, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildActionBar() {
        JPanel bar = new JPanel(new GridLayout(3, 1, 0, 4));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        applyBtn = new JButton("▶   Apply steps  (preview result)");
        applyBtn.setBackground(new Color(60, 130, 70));
        applyBtn.setForeground(Color.WHITE);
        applyBtn.setOpaque(true);
        applyBtn.setFont(applyBtn.getFont().deriveFont(Font.BOLD));
        applyBtn.setEnabled(false);
        applyBtn.addActionListener(e -> applySteps());

        generateBtn = new JButton("🐍  Generate Python code");
        generateBtn.setEnabled(false);
        generateBtn.addActionListener(e -> generateCode());

        exportBtn = new JButton("📤  Export cleaned CSV to disk");
        exportBtn.setEnabled(false);
        exportBtn.addActionListener(e -> exportCleanedCsv());

        bar.add(applyBtn);
        bar.add(generateBtn);
        bar.add(exportBtn);
        return bar;
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    private void addStep() {
        DataSet ds = getDataSet.get();
        if (ds == null) { onStatus.accept("Load a dataset first."); return; }

        int    opIdx     = opSelector.getSelectedIndex();
        int    colIdx    = colSelector.getSelectedIndex();
        // Use the bare column name — never the display string that includes the type badge
        String col       = (colIdx >= 0 && colIdx < actualColumnNames.size())
                           ? actualColumnNames.get(colIdx)
                           : (String) colSelector.getSelectedItem();
        String custom    = customValueField.getText().trim();
        String cast      = (String) castTypeBox.getSelectedItem();
        String ratio     = splitRatioField.getText().trim();
        String sortOrder = sortOrderBox.getSelectedIndex() == 0 ? "ascending" : "descending";
        String filterOp  = (String) filterOperatorBox.getSelectedItem();
        String filterVal = filterValueField.getText().trim();

        if (opIdx == 10) {
            try {
                double r = Double.parseDouble(ratio);
                if (r <= 0 || r >= 1) { onStatus.accept("Train ratio must be between 0 and 1."); return; }
            } catch (NumberFormatException e) {
                onStatus.accept("Train ratio must be a number (e.g. 0.8).");
                return;
            }
        }
        if (opIdx == 14 && filterVal.isEmpty()) {
            onStatus.accept("Enter a filter value before adding this step.");
            return;
        }

        PreprocessingStep step = switch (opIdx) {
            case 0  -> new PreprocessingStep(Operation.DROP_MISSING_ROWS);
            case 1  -> new PreprocessingStep(Operation.FILL_MISSING_MEAN,   col);
            case 2  -> new PreprocessingStep(Operation.FILL_MISSING_MEDIAN, col);
            case 3  -> new PreprocessingStep(Operation.FILL_MISSING_MODE,   col);
            case 4  -> new PreprocessingStep(Operation.FILL_MISSING_CUSTOM, col, custom);
            case 5  -> new PreprocessingStep(Operation.REMOVE_DUPLICATES);
            case 6  -> new PreprocessingStep(Operation.REMOVE_OUTLIERS_IQR, col);
            case 7  -> new PreprocessingStep(Operation.NORMALIZE_MINMAX,    col);
            case 8  -> new PreprocessingStep(Operation.NORMALIZE_ZSCORE,    col);
            case 9  -> new PreprocessingStep(Operation.CAST_COLUMN,         col, cast);
            case 10 -> new PreprocessingStep(Operation.TRAIN_TEST_SPLIT,    null, ratio);
            case 11 -> new PreprocessingStep(Operation.LABEL_ENCODE,        col);
            case 12 -> new PreprocessingStep(Operation.ONE_HOT_ENCODE,      col);
            case 13 -> new PreprocessingStep(Operation.SORT_COLUMN,         col, sortOrder);
            case 14 -> new PreprocessingStep(Operation.FILTER_ROWS,         col, filterOp + "|" + filterVal);
            case 15 -> new PreprocessingStep(Operation.NORMALIZE_ROBUST,    col);
            default -> null;
        };
        if (step == null) return;

        pendingSteps.add(step);
        stepListModel.addElement(describeStep(step));
        updateButtonStates();
        notifyStepCount();
        onStatus.accept("Step added. Total: " + pendingSteps.size());
    }

    /** Runs the Java transformation and notifies the coordinator to refresh the Preview tab. */
    private void applySteps() {
        DataSet ds = getDataSet.get();
        if (ds == null)             { onStatus.accept("Load a dataset first."); return; }
        if (pendingSteps.isEmpty()) { onStatus.accept("No steps to apply.");    return; }

        DataSet working = ds.shallowCopy();
        DataCleaner cleaner = new DataCleaner();

        for (PreprocessingStep step : pendingSteps) {
            String col = step.column();
            switch (step.operation()) {
                case DROP_MISSING_ROWS   -> cleaner.dropMissingRows(working);
                case FILL_MISSING_MEAN   -> cleaner.fillMissingWithMean(working, col);
                case FILL_MISSING_MEDIAN -> cleaner.fillMissingWithMedian(working, col);
                case FILL_MISSING_MODE   -> cleaner.fillMissingWithMode(working, col);
                case FILL_MISSING_CUSTOM -> cleaner.fillMissingWith(working, col, step.param());
                case REMOVE_DUPLICATES   -> cleaner.removeDuplicates(working);
                case REMOVE_OUTLIERS_IQR -> cleaner.removeOutliers(working, col);
                case NORMALIZE_MINMAX    -> cleaner.normalizeMinMax(working, col);
                case NORMALIZE_ZSCORE    -> cleaner.normalizeZScore(working, col);
                case CAST_COLUMN         -> cleaner.castColumn(working, col, step.param());
                case TRAIN_TEST_SPLIT    -> { /* Python-only — no Java transformation */ }
                case LABEL_ENCODE        -> cleaner.labelEncode(working, col);
                case ONE_HOT_ENCODE      -> cleaner.oneHotEncode(working, col);
                case SORT_COLUMN         -> cleaner.sortColumn(working, col,
                                               !"descending".equals(step.param()));
                case FILTER_ROWS         -> {
                    String raw = step.param() != null ? step.param() : "==|";
                    int    sep = raw.indexOf('|');
                    String op  = sep > 0 ? raw.substring(0, sep) : "==";
                    String val = sep >= 0 ? raw.substring(sep + 1) : "";
                    cleaner.filterRows(working, col, op, val);
                }
                case NORMALIZE_ROBUST -> cleaner.normalizeRobustScaler(working, col);
            }
        }

        cleanedDataSet = working;
        updateButtonStates();  // enables Export now that we have a cleaned result
        onApplied.accept(working);
        onStatus.accept("Applied " + pendingSteps.size() + " step(s)  ·  "
                + working.getRowCount() + " rows × " + working.getColumnCount() + " cols");
    }

    /** Generates Python code for the current pipeline and sends it to the Code tab. */
    private void generateCode() {
        DataSet ds = getDataSet.get();
        if (ds == null)             { onStatus.accept("Load a dataset first."); return; }
        if (pendingSteps.isEmpty()) { onStatus.accept("No steps to generate code for."); return; }

        String code = new CodeGenerator().generate(ds.getFilePath(), pendingSteps);
        onCodeGenerated.accept(code);
        onStatus.accept("Python code generated  ·  " + pendingSteps.size() + " step(s)");
    }

    /**
     * Writes {@code cleanedDataSet} to {@code <name>_cleaned.csv} alongside
     * the source file. File I/O runs on a {@link SwingWorker} background thread.
     */
    private void exportCleanedCsv() {
        if (cleanedDataSet == null) {
            onStatus.accept("Nothing to export — run 'Apply steps' first.");
            return;
        }

        String sourcePath = getSourcePath.get();
        if (sourcePath == null) return;

        String csvPath = DataExporter.cleanedCsvPath(sourcePath);
        int    rows    = cleanedDataSet.getRowCount();
        onStatus.accept("Exporting…");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws IOException {
                new DataExporter().exportCsv(cleanedDataSet, csvPath);
                LocalFileSystem.getInstance().refreshAndFindFileByPath(csvPath);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    onStatus.accept("Exported " + rows + " rows → " + csvPath);
                } catch (Exception ex) {
                    Messages.showErrorDialog(project,
                            "Could not export cleaned file:\n" + ex.getMessage(),
                            "Data Preprocessor");
                }
            }
        };
        worker.execute();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateButtonStates() {
        boolean hasSteps   = !pendingSteps.isEmpty();
        boolean hasCleaned = cleanedDataSet != null;
        if (applyBtn    != null) applyBtn.setEnabled(hasSteps);
        if (generateBtn != null) generateBtn.setEnabled(hasSteps);
        if (exportBtn   != null) exportBtn.setEnabled(hasCleaned);
    }

    private void notifyStepCount() {
        if (onStepCountChanged != null) onStepCountChanged.accept(pendingSteps.size());
    }

    private String typeLabel(DataType dt) {
        return switch (dt) {
            case NUMERIC -> "NUM";
            case BOOLEAN -> "BOOL";
            case TEXT    -> "TXT";
            default      -> "?";
        };
    }

    private String describeStep(PreprocessingStep s) {
        String col = s.column() != null ? " [" + s.column() + "]" : "";
        String par = s.param()  != null ? " → " + s.param()       : "";
        return switch (s.operation()) {
            case DROP_MISSING_ROWS   -> "Drop rows with missing values";
            case FILL_MISSING_MEAN   -> "Fill missing" + col + " with Mean";
            case FILL_MISSING_MEDIAN -> "Fill missing" + col + " with Median";
            case FILL_MISSING_MODE   -> "Fill missing" + col + " with Mode";
            case FILL_MISSING_CUSTOM -> "Fill missing" + col + par;
            case REMOVE_DUPLICATES   -> "Remove duplicate rows";
            case REMOVE_OUTLIERS_IQR -> "Remove outliers (IQR)" + col;
            case NORMALIZE_MINMAX    -> "Min-Max normalize" + col;
            case NORMALIZE_ZSCORE    -> "Z-Score normalize" + col;
            case CAST_COLUMN         -> "Cast" + col + " to" + par;
            case TRAIN_TEST_SPLIT    -> "Train-test split" + par;
            case LABEL_ENCODE        -> "Label encode" + col;
            case ONE_HOT_ENCODE      -> "One-hot encode" + col;
            case SORT_COLUMN         -> "Sort" + col + " (" + s.param() + ")";
            case FILTER_ROWS         -> {
                String raw = s.param() != null ? s.param() : "==|";
                int    sep = raw.indexOf('|');
                String op  = sep > 0 ? raw.substring(0, sep) : "==";
                String val = sep >= 0 ? raw.substring(sep + 1) : "";
                yield "Filter" + col + " " + op + " \"" + val + "\"";
            }
            case NORMALIZE_ROBUST -> "Robust normalize" + col;
        };
    }
}
