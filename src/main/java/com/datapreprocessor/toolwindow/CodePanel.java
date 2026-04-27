package com.datapreprocessor.toolwindow;

import com.datapreprocessor.engine.DataExporter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
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

    CodePanel(Project project, Supplier<String> getSourcePath, Consumer<String> onStatus) {
        this.project       = project;
        this.getSourcePath = getSourcePath;
        this.onStatus      = onStatus;
        configureCodeArea();
    }

    JComponent getContent() {
        JPanel panel   = new JPanel(new BorderLayout(0, 4));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));

        JButton saveBtn = new JButton("💾 Save as .py file");
        saveBtn.addActionListener(e -> saveAsPythonFile());

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

    /** Replaces the code area content with the generated script. */
    void setCode(String code) {
        codeArea.setText(code);
        codeArea.setCaretPosition(0);
    }

    /** Resets the code area to the placeholder text. */
    void clear() {
        codeArea.setText(PLACEHOLDER);
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
     * Writes the generated script to {@code preprocess_<name>.py} in the same
     * directory as the source file, then opens it in the IDE editor.
     *
     * <p>File I/O and VFS refresh run on a {@link SwingWorker} background
     * thread. {@code FileEditorManager.openFile()} is deferred via
     * {@code invokeLater} to satisfy IntelliJ's write-safe context requirement.</p>
     */
    private void saveAsPythonFile() {
        String code = codeArea.getText();
        if (code.isBlank() || code.equals(PLACEHOLDER)) {
            onStatus.accept("Nothing to save — run 'Apply & Generate Code' first.");
            return;
        }

        String sourcePath = getSourcePath.get();
        if (sourcePath == null) return;

        String pyPath = DataExporter.pythonScriptPath(sourcePath);
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
