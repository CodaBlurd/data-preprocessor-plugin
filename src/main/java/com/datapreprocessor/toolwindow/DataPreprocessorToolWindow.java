package com.datapreprocessor.toolwindow;

import com.datapreprocessor.engine.CodeGenerator;
import com.datapreprocessor.engine.DataCleaner;
import com.datapreprocessor.engine.DataLoader;
import com.datapreprocessor.model.ColumnProfile;
import com.datapreprocessor.model.DataSet;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinator for the "Data Preprocessor" tool window.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Holds all shared state ({@code currentDataSet}, {@code columnProfiles}).</li>
 *   <li>Constructs and wires the five child panels via constructor callbacks.</li>
 *   <li>Orchestrates cross-panel updates (e.g. after Apply, tells PreviewPanel
 *       and CodePanel to refresh).</li>
 *   <li>Owns file-browse and reload I/O (both run on a {@link SwingWorker}).</li>
 * </ul>
 *
 * <h3>Panel structure</h3>
 * <pre>
 * ┌──────────────────────────────────────────────┐
 * │  HeaderBarPanel  (Browse / path / Reload)    │
 * ├──────────────────────────────────────────────┤
 * │  Tab 1 — PreviewPanel                        │
 * │  Tab 2 — ProfilePanel                        │
 * │  Tab 3 — CleanPanel                          │
 * │  Tab 4 — CodePanel                           │
 * ├──────────────────────────────────────────────┤
 * │  Status bar                                  │
 * └──────────────────────────────────────────────┘
 * </pre>
 */
public class DataPreprocessorToolWindow {

    private final Project project;

    // ── Shared state ──────────────────────────────────────────────────────────
    private DataSet             currentDataSet;
    private List<ColumnProfile> columnProfiles = new ArrayList<>();

    // ── Child panels ──────────────────────────────────────────────────────────
    private final HeaderBarPanel headerBar;
    private final PreviewPanel   previewPanel;
    private final ProfilePanel   profilePanel;
    private final CleanPanel     cleanPanel;
    private final CodePanel      codePanel;

    // ── Root UI ───────────────────────────────────────────────────────────────
    private final JPanel        root       = new JPanel(new BorderLayout());
    private final JLabel        statusLabel = new JLabel(" ");
    private final JBTabbedPane  tabs        = new JBTabbedPane();

    // ── Constructor ───────────────────────────────────────────────────────────

    public DataPreprocessorToolWindow(@NotNull Project project) {
        this.project = project;

        // Instantiate panels — inject only what each one needs
        headerBar   = new HeaderBarPanel(this::browseAndLoad, this::reloadCurrentFile);
        previewPanel = new PreviewPanel();
        profilePanel = new ProfilePanel();
        codePanel    = new CodePanel(project, this::getSourcePath, this::setStatus);
        cleanPanel   = new CleanPanel(
                project,
                () -> currentDataSet,
                this::onApplied,           // preview update after Apply
                this::onCodeGenerated,     // code tab update after Generate
                this::getSourcePath,
                this::setStatus,
                this::onStepCountChanged); // tab badge update

        buildUi();
    }

    // =========================================================================
    // Public API (called by factory and actions)
    // =========================================================================

    /**
     * Loads a new dataset: resets all panels, profiles columns, jumps to Preview.
     * Called by {@link com.datapreprocessor.actions.OpenDataFileAction} and the
     * Browse button.
     */
    public void loadDataSet(DataSet ds) {
        currentDataSet = ds;
        columnProfiles = new DataCleaner().profileColumns(ds);

        String fullPath = ds.getFilePath();
        String fileName = new java.io.File(fullPath).getName();
        String ext      = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase()
                : "";

        headerBar.setFileInfo(fileName, fullPath, ext);
        headerBar.setReloadEnabled(true);

        previewPanel.showData(ds);
        profilePanel.refresh(columnProfiles);
        cleanPanel.clearPipeline();
        cleanPanel.onDataSetLoaded(ds, columnProfiles);
        codePanel.clear();

        tabs.setSelectedIndex(0); // jump to Preview so the user sees data immediately

        setStatus("Loaded: " + fullPath
                + " (" + ds.getRowCount() + " rows × " + ds.getColumnCount() + " cols)");

        ReviewPromptService.recordUseAndMaybePrompt(project);
    }

    public DataSet getCurrentDataSet()   { return currentDataSet; }

    /** Returns the pending pipeline steps — used by GeneratePreprocessingCodeAction. */
    public List<com.datapreprocessor.engine.CodeGenerator.PreprocessingStep> getSelectedSteps() {
        return cleanPanel.getSteps();
    }

    /** Returns the root Swing component registered with the tool window. */
    public JComponent getContent() { return root; }

    // =========================================================================
    // UI construction
    // =========================================================================

    private void buildUi() {
        root.add(headerBar.getContent(), BorderLayout.NORTH);

        tabs.addTab("📊 Preview",           previewPanel.getContent());
        tabs.addTab("📋 Column Profiles",   profilePanel.getContent());
        tabs.addTab("🧹 Clean & Transform", cleanPanel.getContent());
        tabs.addTab("🐍 Generated Code",    codePanel.getContent());
        root.add(tabs, BorderLayout.CENTER);

        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        root.add(statusLabel, BorderLayout.SOUTH);
    }

    // =========================================================================
    // File I/O
    // =========================================================================

    private void browseAndLoad() {
        FileChooserDescriptor descriptor =
                new FileChooserDescriptor(true, false, false, false, false, false)
                        .withFileFilter(f -> {
                            String ext = f.getExtension();
                            return "csv".equalsIgnoreCase(ext)
                                    || "xlsx".equalsIgnoreCase(ext)
                                    || "json".equalsIgnoreCase(ext);
                        })
                        .withTitle("Open Data File")
                        .withDescription("Select a CSV, Excel, or JSON file");

        // chooseFiles (async) avoids EDT-blocking VFS refresh on macOS
        FileChooser.chooseFiles(descriptor, project, null, files -> {
            if (files.isEmpty()) return;
            VirtualFile file = files.get(0);
            SwingWorker<DataSet, Void> worker = new SwingWorker<>() {
                @Override protected DataSet doInBackground() throws IOException {
                    return new DataLoader().load(file.getPath());
                }
                @Override protected void done() {
                    try {
                        loadDataSet(get());
                    } catch (Exception ex) {
                        Messages.showErrorDialog(project,
                                "Could not load file:\n" + ex.getMessage(),
                                "Data Preprocessor");
                    }
                }
            };
            worker.execute();
        });
    }

    private void reloadCurrentFile() {
        if (currentDataSet == null || currentDataSet.getFilePath() == null) {
            browseAndLoad();
            return;
        }
        String path = currentDataSet.getFilePath();
        setStatus("Reloading…");
        headerBar.setReloadEnabled(false);

        SwingWorker<DataSet, Void> worker = new SwingWorker<>() {
            @Override protected DataSet doInBackground() throws IOException {
                return new DataLoader().load(path); // format-aware dispatch
            }
            @Override protected void done() {
                try {
                    DataSet fresh = get();
                    currentDataSet = fresh;
                    columnProfiles = new DataCleaner().profileColumns(fresh);

                    String reloadedName = new java.io.File(fresh.getFilePath()).getName();
                    String ext          = reloadedName.contains(".")
                            ? reloadedName.substring(reloadedName.lastIndexOf('.') + 1).toUpperCase()
                            : "";
                    headerBar.setFileInfo(reloadedName, fresh.getFilePath(), ext);
                    headerBar.setReloadEnabled(true);

                    previewPanel.showData(fresh);
                    profilePanel.refresh(columnProfiles);
                    cleanPanel.onDataSetLoaded(fresh, columnProfiles);
                    // Pipeline steps intentionally preserved across reload

                    setStatus("Reloaded: " + fresh.getRowCount()
                            + " rows × " + fresh.getColumnCount() + " cols"
                            + (cleanPanel.getSteps().isEmpty() ? "" : " · pipeline preserved"));
                } catch (Exception ex) {
                    headerBar.setReloadEnabled(true);
                    Messages.showErrorDialog(project,
                            "Reload failed:\n" + ex.getMessage(),
                            "Data Preprocessor");
                }
            }
        };
        worker.execute();
    }

    // =========================================================================
    // Cross-panel callbacks
    // =========================================================================

    /**
     * Called by {@link CleanPanel} after Apply — updates the Preview tab only
     * and jumps to it so the user can inspect the transformed data immediately.
     */
    private void onApplied(DataSet cleaned) {
        previewPanel.showData(cleaned);
        tabs.setSelectedIndex(0); // jump to Preview tab
    }

    /**
     * Called by {@link CleanPanel} after Generate — pushes the Python source
     * into the Code tab and jumps to it.
     */
    private void onCodeGenerated(String code) {
        codePanel.setCode(code);
        tabs.setSelectedIndex(3); // jump to Generated Code tab
    }

    /**
     * Called by {@link CleanPanel} whenever the pipeline step count changes.
     * Updates the tab label with a badge showing the pending step count.
     */
    private void onStepCountChanged(int count) {
        String label = count > 0
                ? "🧹 Clean & Transform (" + count + ")"
                : "🧹 Clean & Transform";
        SwingUtilities.invokeLater(() -> tabs.setTitleAt(2, label));
    }

    /** Shared status bar writer — passed as a lambda to all child panels. */
    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(" " + msg));
    }

    /** Returns the file path of the currently loaded dataset, or {@code null}. */
    private String getSourcePath() {
        return currentDataSet != null ? currentDataSet.getFilePath() : null;
    }
}
