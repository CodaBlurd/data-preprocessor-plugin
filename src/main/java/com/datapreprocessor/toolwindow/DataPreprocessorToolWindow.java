package com.datapreprocessor.toolwindow;

import com.datapreprocessor.engine.CodeGenerator;
import com.datapreprocessor.engine.CodeGenerator.Operation;
import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;
import com.datapreprocessor.engine.DataCleaner;
import com.datapreprocessor.engine.DataExporter;
import com.datapreprocessor.engine.DataLoader;
import com.datapreprocessor.model.ColumnProfile;
import com.datapreprocessor.model.DataSet;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * The main Swing panel displayed inside the "Data Preprocessor" tool window.
 *
 * <h3>Layout (top → bottom)</h3>
 * <pre>
 * ┌──────────────────────────────────────────────┐
 * │  [Browse CSV…]  path label         [Load]    │  ← header bar
 * ├──────────────────────────────────────────────┤
 * │  Tab 1: Data Preview (first 200 rows)        │
 * │  Tab 2: Column Profiles                      │
 * │  Tab 3: Clean & Transform                    │
 * │  Tab 4: Generated Code                       │
 * └──────────────────────────────────────────────┘
 * </pre>
 */
public class DataPreprocessorToolWindow {

    // Max rows shown in the preview table to keep the UI fast
    private static final int PREVIEW_ROWS = 200;

    // Card names for the Preview tab
    private static final String CARD_EMPTY = "empty";
    private static final String CARD_TABLE = "table";

    // Preview card panel (switches between the demo GIF and the data table)
    private JPanel previewCard;

    private final Project project;

    // Root panel returned to the ToolWindowFactory
    private final JPanel root = new JPanel(new BorderLayout());

    // State
    private DataSet currentDataSet;
    private DataSet cleanedDataSet;   // last result of Apply — used by Export CSV
    private List<ColumnProfile> columnProfiles = new ArrayList<>();
    private final List<PreprocessingStep> pendingSteps = new ArrayList<>();

    // ── UI components ────────────────────────────────────────────────────────

    private final JLabel    pathLabel    = new JLabel("No file loaded");
    private final JBTable   previewTable = new JBTable();
    private final JBTable   profileTable = new JBTable();
    private final JTextArea codeArea     = new JTextArea();
    private final JLabel    statusLabel  = new JLabel(" ");

    // Kept as fields so they can be enabled/disabled based on state
    private JButton reloadBtn;
    private JButton applyBtn;

    // The tab pane — kept as a field so loadDataSet can switch to Preview tab
    private JBTabbedPane tabs;

    // Operation controls
    private final ComboBox<String> colSelector  = new ComboBox<>();
    private final ComboBox<String> opSelector   = new ComboBox<>(new String[]{
            "Drop rows with any missing value",
            "Fill missing → Mean",
            "Fill missing → Median",
            "Fill missing → Mode",
            "Fill missing → Custom value",
            "Remove duplicate rows",
            "Remove outliers (IQR)",
            "Normalize: Min-Max [0,1]",
            "Normalize: Z-Score",
            "Cast column type…"
    });
    private final JTextField customValueField = new JTextField(10);
    private final ComboBox<String> castTypeBox = new ComboBox<>(new String[]{
            "int", "float", "boolean", "string"
    });
    private final DefaultListModel<String> stepListModel = new DefaultListModel<>();
    private final JList<String> stepList = new JList<>(stepListModel);

    // ── Constructor ──────────────────────────────────────────────────────────

    public DataPreprocessorToolWindow(@NotNull Project project) {
        this.project = project;
        buildUi();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Called when a new CSV file is opened (Browse button or right-click action).
     * Resets the full UI state including the pipeline.
     */
    public void loadDataSet(DataSet ds) {
        this.currentDataSet = ds;
        this.columnProfiles = new DataCleaner().profileColumns(ds);
        pendingSteps.clear();
        stepListModel.clear();
        cleanedDataSet = null;

        // Update path label regardless of how the file was opened
        pathLabel.setText(ds.getFilePath());

        refreshPreview();
        refreshProfileTable();
        refreshColumnSelector();
        updateApplyButtonState();

        // Enable Reload now that we have a file to reload from
        if (reloadBtn != null) reloadBtn.setEnabled(true);

        // Jump to Preview tab so the user sees the data immediately
        if (tabs != null) tabs.setSelectedIndex(0);

        setStatus("Loaded: " + ds.getFilePath()
                + " (" + ds.getRowCount() + " rows × " + ds.getColumnCount() + " cols)");
        ReviewPromptService.recordUseAndMaybePrompt(project);
    }

    /**
     * Called by the Reload button. Re-reads the CSV from disk but preserves
     * the existing pipeline steps so users don't lose their work.
     */
    private void refreshFromDisk() {
        if (currentDataSet == null || currentDataSet.getFilePath() == null) return;
        String path = currentDataSet.getFilePath();
        setStatus("Reloading…");
        reloadBtn.setEnabled(false);

        SwingWorker<DataSet, Void> worker = new SwingWorker<>() {
            @Override
            protected DataSet doInBackground() throws IOException {
                return new DataLoader().loadCsv(path);
            }
            @Override
            protected void done() {
                try {
                    DataSet fresh = get();
                    // Update data + profiles but keep pipeline intact
                    currentDataSet = fresh;
                    columnProfiles = new DataCleaner().profileColumns(fresh);
                    cleanedDataSet = null;
                    pathLabel.setText(fresh.getFilePath());
                    refreshPreview();
                    refreshProfileTable();
                    refreshColumnSelector();
                    reloadBtn.setEnabled(true);
                    setStatus("Reloaded: " + fresh.getRowCount()
                            + " rows × " + fresh.getColumnCount() + " cols"
                            + (pendingSteps.isEmpty() ? "" : " · pipeline preserved"));
                } catch (Exception ex) {
                    reloadBtn.setEnabled(true);
                    Messages.showErrorDialog(project,
                            "Reload failed:\n" + ex.getMessage(),
                            "Data Preprocessor");
                }
            }
        };
        worker.execute();
    }

    public DataSet getCurrentDataSet() { return currentDataSet; }

    /** Returns the list of pending preprocessing steps for code generation. */
    public List<PreprocessingStep> getSelectedSteps() {
        return new ArrayList<>(pendingSteps);
    }

    /** Returns the root Swing component registered with the tool window. */
    public JComponent getContent() { return root; }

    // =========================================================================
    // UI construction
    // =========================================================================

    private void buildUi() {
        root.add(buildHeaderBar(), BorderLayout.NORTH);

        tabs = new JBTabbedPane();
        tabs.addTab("📊 Preview",           buildPreviewTab());
        tabs.addTab("📋 Column Profiles",   buildProfileTab());
        tabs.addTab("🧹 Clean & Transform", buildCleanTab());
        tabs.addTab("🐍 Generated Code",    buildCodeTab());
        root.add(tabs, BorderLayout.CENTER);

        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        root.add(statusLabel, BorderLayout.SOUTH);
    }

    // ── Header bar ────────────────────────────────────────────────────────────

    private JPanel buildHeaderBar() {
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JButton browseBtn = new JButton("Browse CSV…");
        browseBtn.addActionListener(e -> browseAndLoad());

        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.ITALIC, 11f));

        reloadBtn = new JButton("Reload");
        reloadBtn.setEnabled(false);   // enabled once a file is loaded
        reloadBtn.setToolTipText("Re-read the CSV from disk (preserves your pipeline steps)");
        reloadBtn.addActionListener(e -> reloadCurrentFile());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(reloadBtn);

        bar.add(browseBtn, BorderLayout.WEST);
        bar.add(pathLabel,  BorderLayout.CENTER);
        bar.add(right,      BorderLayout.EAST);
        return bar;
    }

    // ── Tab 1: Preview ────────────────────────────────────────────────────────

    private JComponent buildPreviewTab() {
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        previewTable.setFillsViewportHeight(true);

        previewCard = new JPanel(new CardLayout());

        // ── Empty state: animated demo GIF ───────────────────────────────────
        JPanel emptyPane = new JPanel(new GridBagLayout());
        URL gifUrl = getClass().getResource("/demo_embed.gif");
        if (gifUrl != null) {
            // ImageIcon natively animates GIFs — no extra libraries needed
            ImageIcon animatedIcon = new ImageIcon(gifUrl);
            JLabel gifLabel = new JLabel(animatedIcon);

            JPanel inner = new JPanel();
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setOpaque(false);
            gifLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            inner.add(gifLabel);
            inner.add(Box.createVerticalStrut(10));

            JLabel hint = new JLabel("Browse a CSV file above, or right-click any .csv → Open in Data Preprocessor");
            hint.setForeground(JBColor.GRAY);
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);
            inner.add(hint);

            emptyPane.add(inner);
        } else {
            // Fallback if resource is missing (e.g. dev mode without full build)
            emptyPane.add(new JLabel("Load a CSV file to get started."));
        }

        previewCard.add(emptyPane,                   CARD_EMPTY);
        previewCard.add(new JBScrollPane(previewTable), CARD_TABLE);

        return previewCard;
    }

    // ── Tab 2: Profiles ───────────────────────────────────────────────────────

    private JComponent buildProfileTab() {
        profileTable.setFillsViewportHeight(true);
        return new JBScrollPane(profileTable);
    }

    // ── Tab 3: Clean & Transform ──────────────────────────────────────────────

    private JComponent buildCleanTab() {
        // Outer panel: form at top, step list in centre, action bar pinned at bottom
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ── Step builder form ────────────────────────────────────────────────
        JPanel builder = new JPanel(new GridBagLayout());
        builder.setBorder(BorderFactory.createTitledBorder("1.  Choose an operation and add it"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
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

        customLabel.setVisible(false);
        customValueField.setVisible(false);
        castLabel.setVisible(false);
        castTypeBox.setVisible(false);

        opSelector.addActionListener(e -> {
            int idx = opSelector.getSelectedIndex();
            customLabel.setVisible(idx == 4);
            customValueField.setVisible(idx == 4);
            castLabel.setVisible(idx == 9);
            castTypeBox.setVisible(idx == 9);
        });

        // "Add Step" button — full width
        JButton addBtn = new JButton("➕  Add step to pipeline");
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.weightx = 1;
        builder.add(addBtn, gbc);
        addBtn.addActionListener(e -> addStep());

        panel.add(builder, BorderLayout.NORTH);

        // ── Step list (scrollable, takes remaining height) ───────────────────
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createTitledBorder("2.  Pipeline steps  (applied top → bottom)"));
        stepList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listPanel.add(new JBScrollPane(stepList), BorderLayout.CENTER);

        JPanel listMgmt = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton removeBtn = new JButton("Remove selected");
        JButton clearBtn  = new JButton("Clear all");
        removeBtn.addActionListener(e -> {
            int sel = stepList.getSelectedIndex();
            if (sel >= 0) {
                stepListModel.remove(sel);
                pendingSteps.remove(sel);
                updateApplyButtonState();
            }
        });
        clearBtn.addActionListener(e -> {
            stepListModel.clear();
            pendingSteps.clear();
            updateApplyButtonState();
        });
        listMgmt.add(removeBtn);
        listMgmt.add(clearBtn);
        listPanel.add(listMgmt, BorderLayout.SOUTH);

        panel.add(listPanel, BorderLayout.CENTER);

        // ── Action bar — always visible, pinned to the bottom ────────────────
        JPanel actionBar = new JPanel(new GridLayout(2, 1, 0, 4));
        actionBar.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        applyBtn = new JButton("▶   Apply steps & generate Python code");
        JButton exportCsvBtn = new JButton("📤  Export cleaned CSV to disk");

        applyBtn.setBackground(new Color(60, 130, 70));
        applyBtn.setForeground(Color.WHITE);
        applyBtn.setOpaque(true);
        applyBtn.setFont(applyBtn.getFont().deriveFont(Font.BOLD));
        applyBtn.setEnabled(false);   // enabled once at least one step is added

        applyBtn.addActionListener(e -> applyAndGenerate());
        exportCsvBtn.addActionListener(e -> exportCleanedCsv());

        actionBar.add(applyBtn);
        actionBar.add(exportCsvBtn);

        panel.add(actionBar, BorderLayout.SOUTH);
        return panel;
    }

    // ── Tab 4: Generated Code ─────────────────────────────────────────────────

    private JComponent buildCodeTab() {
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        codeArea.setEditable(false);
        codeArea.setText("# Apply steps in the 'Clean & Transform' tab to generate code.");

        JPanel panel = new JPanel(new BorderLayout(0, 4));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));

        JButton saveBtn = new JButton("💾 Save as .py file");
        saveBtn.addActionListener(e -> saveAsPythonFile());

        JButton copyBtn = new JButton("Copy to clipboard");
        copyBtn.addActionListener(e -> {
            codeArea.selectAll();
            codeArea.copy();
            codeArea.select(0, 0);
            setStatus("Code copied to clipboard.");
        });

        toolbar.add(saveBtn);
        toolbar.add(copyBtn);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JBScrollPane(codeArea), BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // Event handlers
    // =========================================================================

    private void browseAndLoad() {
        VirtualFile file = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor("csv"),
                project, null);
        if (file == null) return;

        pathLabel.setText(file.getPath());
        SwingWorker<DataSet, Void> worker = new SwingWorker<>() {
            @Override
            protected DataSet doInBackground() throws IOException {
                return new DataLoader().loadCsv(file.getPath());
            }
            @Override
            protected void done() {
                try { loadDataSet(get()); }
                catch (Exception ex) {
                    Messages.showErrorDialog(project,
                            "Could not load CSV:\n" + ex.getMessage(),
                            "Data Preprocessor");
                }
            }
        };
        worker.execute();
    }

    private void reloadCurrentFile() {
        if (currentDataSet == null || currentDataSet.getFilePath() == null) {
            // No file loaded yet — open the file chooser instead
            browseAndLoad();
            return;
        }
        refreshFromDisk();
    }

    private void addStep() {
        if (currentDataSet == null) {
            setStatus("Load a dataset first.");
            return;
        }

        int    opIdx  = opSelector.getSelectedIndex();
        String col    = (String) colSelector.getSelectedItem();
        String custom = customValueField.getText().trim();
        String cast   = (String) castTypeBox.getSelectedItem();

        PreprocessingStep step = switch (opIdx) {
            case 0  -> new PreprocessingStep(Operation.DROP_MISSING_ROWS);
            case 1  -> new PreprocessingStep(Operation.FILL_MISSING_MEAN, col);
            case 2  -> new PreprocessingStep(Operation.FILL_MISSING_MEDIAN, col);
            case 3  -> new PreprocessingStep(Operation.FILL_MISSING_MODE, col);
            case 4  -> new PreprocessingStep(Operation.FILL_MISSING_CUSTOM, col, custom);
            case 5  -> new PreprocessingStep(Operation.REMOVE_DUPLICATES);
            case 6  -> new PreprocessingStep(Operation.REMOVE_OUTLIERS_IQR, col);
            case 7  -> new PreprocessingStep(Operation.NORMALIZE_MINMAX, col);
            case 8  -> new PreprocessingStep(Operation.NORMALIZE_ZSCORE, col);
            case 9  -> new PreprocessingStep(Operation.CAST_COLUMN, col, cast);
            default -> null;
        };
        if (step == null) return;

        pendingSteps.add(step);
        stepListModel.addElement(describeStep(step));
        updateApplyButtonState();
        setStatus("Step added. Total: " + pendingSteps.size());
    }

    private void applyAndGenerate() {
        if (currentDataSet == null) { setStatus("Load a dataset first."); return; }
        if (pendingSteps.isEmpty()) { setStatus("No steps to apply."); return; }

        // Work on a copy to keep the original intact for re-runs
        DataSet working = currentDataSet.shallowCopy();
        DataCleaner cleaner = new DataCleaner();

        for (PreprocessingStep step : pendingSteps) {
            String col = step.column();
            switch (step.operation()) {
                case DROP_MISSING_ROWS      -> cleaner.dropMissingRows(working);
                case FILL_MISSING_MEAN      -> cleaner.fillMissingWithMean(working, col);
                case FILL_MISSING_MEDIAN    -> cleaner.fillMissingWithMedian(working, col);
                case FILL_MISSING_MODE      -> cleaner.fillMissingWithMode(working, col);
                case FILL_MISSING_CUSTOM    -> cleaner.fillMissingWith(working, col, step.param());
                case REMOVE_DUPLICATES      -> cleaner.removeDuplicates(working);
                case REMOVE_OUTLIERS_IQR    -> cleaner.removeOutliers(working, col);
                case NORMALIZE_MINMAX       -> cleaner.normalizeMinMax(working, col);
                case NORMALIZE_ZSCORE       -> cleaner.normalizeZScore(working, col);
                case CAST_COLUMN            -> cleaner.castColumn(working, col, step.param());
            }
        }

        // Store cleaned result so Export CSV can use it without re-running
        cleanedDataSet = working;

        // Update preview with cleaned data
        renderPreviewTable(working);

        // Generate code
        String code = new CodeGenerator().generate(
                currentDataSet.getFilePath(), pendingSteps);
        codeArea.setText(code);

        setStatus("Applied " + pendingSteps.size() + " steps. "
                + working.getRowCount() + " rows remain. "
                + "Use '📤 Export cleaned CSV' or '💾 Save as .py' to save.");
    }

    // =========================================================================
    // Export handlers
    // =========================================================================

    /**
     * Writes the generated Python code to a {@code preprocess_<name>.py} file
     * in the same directory as the source CSV, then opens it in the editor.
     */
    private void saveAsPythonFile() {
        String code = codeArea.getText();
        if (code.isBlank() || code.startsWith("# Apply steps")) {
            setStatus("Nothing to save — run 'Apply & Generate Code' first.");
            return;
        }
        if (currentDataSet == null) return;

        String pyPath = DataExporter.pythonScriptPath(currentDataSet.getFilePath());
        try {
            new DataExporter().exportPythonScript(code, pyPath);

            // Refresh IntelliJ's virtual file system so the new file appears
            // in the Project view immediately, then open it in the editor.
            VirtualFile vf = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(pyPath);
            if (vf != null) {
                FileEditorManager.getInstance(project).openFile(vf, true);
            }
            setStatus("Saved: " + pyPath);
        } catch (IOException ex) {
            Messages.showErrorDialog(project,
                    "Could not save Python file:\n" + ex.getMessage(),
                    "Data Preprocessor");
        }
    }

    /**
     * Writes the cleaned dataset to a {@code <name>_cleaned.csv} file in the
     * same directory as the source CSV, then refreshes the project view.
     */
    private void exportCleanedCsv() {
        if (cleanedDataSet == null) {
            setStatus("Nothing to export — run 'Apply & Generate Code' first.");
            return;
        }

        String csvPath = DataExporter.cleanedCsvPath(currentDataSet.getFilePath());
        try {
            new DataExporter().exportCsv(cleanedDataSet, csvPath);

            // Make the new file visible in the Project view immediately
            VirtualFile vf = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(csvPath);
            if (vf != null) {
                vf.refresh(false, false);
            }
            setStatus("Exported " + cleanedDataSet.getRowCount()
                    + " rows → " + csvPath);
        } catch (IOException ex) {
            Messages.showErrorDialog(project,
                    "Could not export CSV:\n" + ex.getMessage(),
                    "Data Preprocessor");
        }
    }

    // =========================================================================
    // Table refresh helpers
    // =========================================================================

    private void refreshPreview() {
        if (currentDataSet != null) renderPreviewTable(currentDataSet);
    }

    private void renderPreviewTable(DataSet ds) {
        int rowCount = Math.min(PREVIEW_ROWS, ds.getRowCount());
        String[] cols = ds.getHeaders().toArray(new String[0]);
        Object[][] data = new Object[rowCount][ds.getColumnCount()];
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < ds.getColumnCount(); c++) {
                data[r][c] = ds.getValue(r, c);
            }
        }
        previewTable.setModel(new DefaultTableModel(data, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        // Switch the card so the data table is visible instead of the demo GIF
        if (previewCard != null) {
            ((CardLayout) previewCard.getLayout()).show(previewCard, CARD_TABLE);
        }
    }

    private void refreshProfileTable() {
        String[] profileCols = {
                "Column", "Type", "Total", "Nulls", "Null %",
                "Unique", "Mean", "Median", "Std Dev", "Min", "Max", "Most Common"
        };
        Object[][] rows = new Object[columnProfiles.size()][profileCols.length];
        for (int i = 0; i < columnProfiles.size(); i++) {
            ColumnProfile p = columnProfiles.get(i);
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
        profileTable.setModel(new DefaultTableModel(rows, profileCols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
    }

    private void refreshColumnSelector() {
        colSelector.removeAllItems();
        if (currentDataSet != null) {
            for (String h : currentDataSet.getHeaders()) colSelector.addItem(h);
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

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
        };
    }

    private String fmt(double d) {
        if (Double.isNaN(d)) return "—";
        return d == Math.floor(d) ? String.valueOf((long) d)
                                  : String.format("%.4f", d);
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(" " + msg));
    }

    /** Enables the Apply button only when there is at least one pending step. */
    private void updateApplyButtonState() {
        if (applyBtn != null) {
            applyBtn.setEnabled(!pendingSteps.isEmpty());
        }
    }
}
