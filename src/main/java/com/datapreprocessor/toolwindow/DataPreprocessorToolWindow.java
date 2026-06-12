package com.datapreprocessor.toolwindow;

import com.datapreprocessor.engine.DataCleaner;
import com.datapreprocessor.engine.DataLoader;
import com.datapreprocessor.licensing.ProFeature;
import com.datapreprocessor.licensing.ProFeatureGate;
import com.datapreprocessor.licensing.ProUpgradeUi;
import com.datapreprocessor.model.ColumnProfile;
import com.datapreprocessor.model.DataSet;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.LicensingFacade;
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
 *   <li>Constructs and wires the six child panels via constructor callbacks.</li>
 *   <li>Orchestrates cross-panel updates (e.g. after Apply, tells PreviewPanel,
 *       and VisualisationPanel to refresh).</li>
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
 * │  Tab 5 — VisualisationPanel                  │
 * ├──────────────────────────────────────────────┤
 * │  Status bar                                  │
 * └──────────────────────────────────────────────┘
 * </pre>
 */
public class DataPreprocessorToolWindow {

    private final Project project;
    private final Disposable parentDisposable;

    // ── Shared state ──────────────────────────────────────────────────────────
    private DataSet             currentDataSet;
    private List<ColumnProfile> columnProfiles = new ArrayList<>();

    // ── Child panels ──────────────────────────────────────────────────────────
    private final HeaderBarPanel headerBar;
    private final PreviewPanel   previewPanel;
    private final ProfilePanel   profilePanel;
    private final CleanPanel     cleanPanel;
    private final CodePanel      codePanel;
    private final VisualisationPanel visualisationPanel;
    private final JComponent     visualiseUnlockedContent;

    // ── Root UI ───────────────────────────────────────────────────────────────
    private final JPanel        root       = new JPanel(new BorderLayout());
    private final JLabel        statusLabel = new JLabel(" ");
    private final JBTabbedPane  tabs        = new JBTabbedPane();
    private JComponent          visualiseContent;
    private boolean             visualiseLicensePromptPending;

    // ── Constructor ───────────────────────────────────────────────────────────

    public DataPreprocessorToolWindow(@NotNull Project project, @NotNull Disposable parentDisposable) {
        this.project = project;
        this.parentDisposable = parentDisposable;

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
                this::onRCodeGenerated,
                this::onSqlCodeGenerated,
                this::getSourcePath,
                this::setStatus,
                this::onStepCountChanged); // tab badge update

        visualisationPanel = new VisualisationPanel();
        visualiseUnlockedContent = visualisationPanel.getContent();

        buildUi();
        subscribeToLicenseChanges();
    }

    // =========================================================================
    // Public API (called by factory and actions)
    // =========================================================================

    /**
     * Loads a new dataset using pre-computed column profiles.
     *
     * <p>This is the primary entry point.  Both the Browse button ({@link #browseAndLoad})
     * and {@link com.datapreprocessor.actions.OpenDataFileAction} compute profiles on a
     * background thread and call this overload so that the potentially O(n·columns)
     * profiling work never blocks the EDT.</p>
     *
     * @param ds       the freshly loaded dataset
     * @param profiles column profiles already computed off the EDT
     */
    public void loadDataSet(DataSet ds, List<ColumnProfile> profiles) {
        currentDataSet = ds;
        columnProfiles = profiles;

        String fullPath = ds.getFilePath();
        String fileName = new java.io.File(fullPath).getName();
        String ext      = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase()
                : "";

        headerBar.setFileInfo(fileName, fullPath, ext);
        headerBar.setReloadEnabled(true);

        previewPanel.showData(ds);
        profilePanel.refresh(columnProfiles);
        visualisationPanel.onDataSetLoaded(ds, columnProfiles);
        cleanPanel.clearPipeline();
        cleanPanel.onDataSetLoaded(ds, columnProfiles);
        codePanel.clear();

        tabs.setSelectedIndex(0); // jump to Preview so the user sees data immediately

        setStatus("Loaded: " + fullPath
                + " (" + ds.getRowCount() + " rows × " + ds.getColumnCount() + " cols)");

        ReviewPromptService.recordUseAndMaybePrompt(project);
    }

    /**
     * Convenience overload for callers that cannot supply pre-computed profiles.
     *
     * <p><b>Warning:</b> {@link DataCleaner#profileColumns} is O(n·columns) and runs
     * on the calling thread.  Prefer {@link #loadDataSet(DataSet, List)} and compute
     * profiles in a swing worker doInBackground() block.</p>
     */
    public void loadDataSet(DataSet ds) {
        loadDataSet(ds, new DataCleaner().profileColumns(ds));
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

        JComponent cleanContent = cleanPanel.getContent();
        tabs.addTab("📊 Preview",           previewPanel.getContent());
        tabs.addTab("📋 Column Profiles",   profilePanel.getContent());
        tabs.addTab("🧹 Clean & Transform", cleanContent);
        tabs.addTab("💻 Generated Code",    codePanel.getContent());
        visualiseContent = visualiseTabContent();
        tabs.addTab("📈 Visualise  [Pro]", visualiseContent);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == cleanContent) {
                cleanPanel.refreshSettingsDefaults();
            }
            if (tabs.getSelectedComponent() == visualiseContent
                    && ProFeatureGate.isUnlocked(ProFeature.VISUALISATIONS)) {
                refreshVisualiseTabForLicense();
            } else if (tabs.getSelectedComponent() == visualiseContent
                    && !visualiseLicensePromptPending) {
                visualiseLicensePromptPending = true;
                SwingUtilities.invokeLater(() -> {
                    visualiseLicensePromptPending = false;
                    if (tabs.getSelectedComponent() == visualiseContent) {
                        if (ProFeatureGate.isUnlocked(ProFeature.VISUALISATIONS)) {
                            refreshVisualiseTabForLicense();
                            return;
                        }
                        ProUpgradeUi.showLockedDialog(project);
                    }
                });
            }
        });
        root.add(tabs, BorderLayout.CENTER);

        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        root.add(statusLabel, BorderLayout.SOUTH);
    }

    private JComponent visualiseTabContent() {
        if (ProFeatureGate.isUnlocked(ProFeature.VISUALISATIONS)) {
            return visualiseUnlockedContent;
        }

        JPanel panel = new JPanel(new BorderLayout());
        JPanel content = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 10, 0);

        JLabel message = new JLabel(
                "<html><center><b>" + ProFeatureGate.lockedMessage(ProFeature.VISUALISATIONS) + "</b><br><br>"
                        + ProFeatureGate.unlockHint() + "</center></html>",
                SwingConstants.CENTER
        );
        message.setForeground(JBColor.GRAY);
        content.add(message, gbc);

        JButton upgradeButton = new JButton("Upgrade to Pro");
        upgradeButton.addActionListener(e -> {
            if (ProFeatureGate.isUnlocked(ProFeature.VISUALISATIONS)) {
                refreshVisualiseTabForLicense();
                return;
            }
            ProUpgradeUi.openLicenseManager(project);
        });
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        content.add(upgradeButton, gbc);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private void subscribeToLicenseChanges() {
        ApplicationManager.getApplication()
                .getMessageBus()
                .connect(parentDisposable)
                .subscribe(LicensingFacade.LicenseStateListener.TOPIC,
                        new LicensingFacade.LicenseStateListener() {
                            @Override
                            public void licenseStateChanged(LicensingFacade newState) {
                                SwingUtilities.invokeLater(DataPreprocessorToolWindow.this::refreshVisualiseTabForLicense);
                            }
                        });
    }

    private void refreshVisualiseTabForLicense() {
        if (!ProFeatureGate.isUnlocked(ProFeature.VISUALISATIONS)
                || visualiseContent == visualiseUnlockedContent) {
            return;
        }

        int index = tabs.indexOfComponent(visualiseContent);
        if (index < 0) {
            return;
        }

        visualiseContent = visualiseUnlockedContent;
        tabs.setComponentAt(index, visualiseContent);
        if (currentDataSet != null) {
            visualisationPanel.onDataSetLoaded(currentDataSet, columnProfiles);
        }
        tabs.setSelectedComponent(visualiseContent);
    }

    // =========================================================================
    // File I/O
    // =========================================================================

    private void browseAndLoad() {
        FileChooserDescriptor descriptor =
                new FileChooserDescriptor(true, false, false, false, false, false)
                        .withFileFilter(f -> {
                            if (f.isDirectory()) return true;
                            String ext = f.getExtension();
                            return "csv".equalsIgnoreCase(ext)
                                    || "xlsx".equalsIgnoreCase(ext)
                                    || "json".equalsIgnoreCase(ext);
                        })
                        .withTitle("Open Data File")
                        .withDescription("Select a CSV, Excel, or JSON file");
//        descriptor.setForcedToUseIdeaFileChooser(true);

        FileChooser.chooseFile(descriptor, project, root, null, file -> {
            if (file == null) return;
            // Both file I/O and column profiling are O(n) — keep both off the EDT.
            SwingWorker<ReloadResult, Void> worker = new SwingWorker<>() {
                @Override protected ReloadResult doInBackground() throws IOException {
                    DataSet ds = new DataLoader().load(file.getPath());
                    List<ColumnProfile> profiles = new DataCleaner().profileColumns(ds);
                    return new ReloadResult(ds, profiles);
                }
                @Override protected void done() {
                    try {
                        ReloadResult result = get();
                        loadDataSet(result.dataSet(), result.profiles());
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

    /** Pairs a freshly loaded dataset with its pre-computed column profiles. */
    private record ReloadResult(DataSet dataSet, List<ColumnProfile> profiles) {}

    private void reloadCurrentFile() {
        if (currentDataSet == null || currentDataSet.getFilePath() == null) {
            browseAndLoad();
            return;
        }
        String path = currentDataSet.getFilePath();
        setStatus("Reloading…");
        headerBar.setReloadEnabled(false);

        SwingWorker<ReloadResult, Void> worker = new SwingWorker<>() {
            @Override protected ReloadResult doInBackground() throws IOException {
                // Both file I/O and column profiling are O(n) — keep both off the EDT.
                DataSet fresh = new DataLoader().load(path);
                List<ColumnProfile> profiles = new DataCleaner().profileColumns(fresh);
                return new ReloadResult(fresh, profiles);
            }
            @Override protected void done() {
                try {
                    ReloadResult result = get();
                    DataSet fresh = result.dataSet();
                    currentDataSet = fresh;
                    columnProfiles = result.profiles();

                    String reloadedName = new java.io.File(fresh.getFilePath()).getName();
                    String ext          = reloadedName.contains(".")
                            ? reloadedName.substring(reloadedName.lastIndexOf('.') + 1).toUpperCase()
                            : "";
                    headerBar.setFileInfo(reloadedName, fresh.getFilePath(), ext);
                    headerBar.setReloadEnabled(true);

                    previewPanel.showData(fresh);
                    profilePanel.refresh(columnProfiles);
                    visualisationPanel.onDataSetLoaded(fresh, columnProfiles);
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
     * Called by {@link CleanPanel} after Apply — updates the Preview tab and
     * jumps to it so the user can inspect the transformed data immediately.
     *
     * <p>Re-profiling the cleaned dataset for the Visualise tab is deferred to a
     * background thread: {@link DataCleaner#profileColumns} is O(n·columns) and
     * must not block the event dispatch thread.</p>
     */
    private void onApplied(DataSet cleaned) {
        previewPanel.showData(cleaned);
        profilePanel.showLoading();
        tabs.setSelectedIndex(0); // jump to Preview immediately — don't wait for profiling
        setStatus("Applied. Refreshing cleaned column profiles…");

        new SwingWorker<List<ColumnProfile>, Void>() {
            @Override protected List<ColumnProfile> doInBackground() {
                return new DataCleaner().profileColumns(cleaned);
            }
            @Override protected void done() {
                try {
                    List<ColumnProfile> cleanedProfiles = get();
                    columnProfiles = cleanedProfiles;
                    profilePanel.refresh(cleanedProfiles);
                    visualisationPanel.onDataSetLoaded(cleaned, cleanedProfiles);
                }
                catch (Exception ex) {
                    setStatus("Applied, but profile refresh failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Called by {@link CleanPanel} after Generate — pushes the Python source
     * into the Code tab and jumps to it.
     */
    private void onCodeGenerated(String code) {
        codePanel.setCode(code, "py");
        tabs.setSelectedIndex(3); // jump to Generated Code tab
    }

    private void onRCodeGenerated(String code) {
        codePanel.setCode(code, "R");
        tabs.setSelectedIndex(3); // jump to Generated Code tab
    }

    private void onSqlCodeGenerated(String code) {
        codePanel.setCode(code, "sql");
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
