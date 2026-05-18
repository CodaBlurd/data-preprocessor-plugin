package com.datapreprocessor.toolwindow;

import com.datapreprocessor.engine.DataExporter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tab 4 — Generated Code.
 *
 * <p>Displays the pandas script produced by
 * {@link com.datapreprocessor.engine.CodeGenerator} and provides
 * Save-as-.py and Copy-to-clipboard actions.</p>
 *
 * <p>Dependencies are injected via the constructor so this panel
 * never reaches into the coordinator directly:</p>
 * <ul>
 *   <li>{@code getSourcePath} — returns the file path of the currently
 *       loaded dataset (used to derive the output {@code .py} path).</li>
 *   <li>{@code onStatus} — forwards status messages to the shared status bar.</li>
 * </ul>
 */
class CodePanel {

    private static final String PLACEHOLDER =
            "# Apply steps in the 'Clean & Transform' tab to generate code.";

    private final Project          project;
    private final Supplier<String> getSourcePath;
    private final Consumer<String> onStatus;

    private final JTextArea codeArea = new JTextArea();

    /** "py" or "R" — tracks which language was last pushed into the code area. */
    private String currentLanguage = "py";

    CodePanel(Project project, Supplier<String> getSourcePath, Consumer<String> onStatus) {
        this.project       = project;
        this.getSourcePath = getSourcePath;
        this.onStatus      = onStatus;
        configureCodeArea();
    }

    JComponent getContent() {
        JPanel panel   = new JPanel(new BorderLayout(0, 4));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));

        JButton saveBtn = new JButton("💾 Save as script…");
        saveBtn.addActionListener(e -> saveAsScriptFile());

        JButton copyBtn = new JButton("Copy to clipboard");
        copyBtn.addActionListener(e -> {
            codeArea.selectAll();
            codeArea.copy();
            codeArea.select(0, 0);
            onStatus.accept("Code copied to clipboard.");
        });

        toolbar.add(saveBtn);
        toolbar.add(copyBtn);
        panel.add(toolbar,                    BorderLayout.NORTH);
        panel.add(new JBScrollPane(codeArea), BorderLayout.CENTER);
        return panel;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Replaces the code area content with the generated script.
     *
     * @param code     the generated source code
     * @param language {@code "py"} for Python, {@code "R"} for R — used to
     *                 derive the correct default filename in the save dialog
     */
    void setCode(String code, String language) {
        codeArea.setText(code);
        codeArea.setCaretPosition(0);
        currentLanguage = (language != null) ? language : "py";
    }

    /** Resets the code area to the placeholder text and clears the language state. */
    void clear() {
        codeArea.setText(PLACEHOLDER);
        currentLanguage = "py";
    }

    /** Returns the current content of the code area. */
    String getCode() {
        return codeArea.getText();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void configureCodeArea() {
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        codeArea.setEditable(false);
        codeArea.setLineWrap(false);
        codeArea.setBackground(JBColor.namedColor("Editor.background", new Color(43, 43, 43)));
        codeArea.setForeground(JBColor.namedColor("Editor.foreground", JBColor.foreground()));
        codeArea.setCaretColor(JBColor.foreground());
        codeArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        codeArea.setText(PLACEHOLDER);
    }

    /**
     * Prompts for a destination, writes the generated script, then opens it in
     * the IDE editor. Accepts both {@code .py} and {@code .R} extensions so the
     * dialog works correctly whether Python or R code was last generated.
     *
     * <p>File I/O and VFS refresh run on a {@link SwingWorker} background
     * thread. {@code FileEditorManager.openFile()} is deferred via
     * {@code invokeLater} to satisfy IntelliJ's write-safe context requirement.</p>
     */
    private void saveAsScriptFile() {
        String code = codeArea.getText();
        if (code.isBlank() || code.equals(PLACEHOLDER)) {
            onStatus.accept("Nothing to save — generate code first.");
            return;
        }

        String sourcePath = getSourcePath.get();
        if (sourcePath == null) return;

        // Default filename matches the language last generated (.py or .R)
        String defaultPath = "R".equals(currentLanguage)
                ? DataExporter.rScriptPath(sourcePath)
                : DataExporter.pythonScriptPath(sourcePath);
        Path defaultOutput = Paths.get(defaultPath);

        FileSaverDescriptor descriptor = new FileSaverDescriptor(
                "Save Script",
                "Choose where to save the generated script"
        );
        descriptor.withExtensionFilter("py", "R");

        FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);

        VirtualFileWrapper selected = dialog.save(
                LocalFileSystem.getInstance().findFileByNioFile(defaultOutput.getParent()),
                defaultOutput.getFileName().toString()
        );

        if (selected == null) {
            onStatus.accept("Save cancelled.");
            return;
        }

        String pyPath = selected.getFile().getAbsolutePath();
        onStatus.accept("Saving…");

        SwingWorker<VirtualFile, Void> worker = new SwingWorker<>() {
            @Override
            protected VirtualFile doInBackground() throws IOException {
                new DataExporter().exportPythonScript(code, pyPath);
                return LocalFileSystem.getInstance().refreshAndFindFileByPath(pyPath);
            }

            @Override
            protected void done() {
                try {
                    VirtualFile vf = get();
                    onStatus.accept("Saved: " + pyPath);
                    if (vf != null) {
                        // invokeLater: FileEditorManager requires a write-safe context
                        ApplicationManager.getApplication().invokeLater(
                                () -> FileEditorManager.getInstance(project).openFile(vf, true));
                    }
                } catch (Exception ex) {
                    Messages.showErrorDialog(project,
                            "Could not save Python file:\n" + ex.getMessage(),
                            "Data Preprocessor");
                }
            }
        };
        worker.execute();
    }
}
