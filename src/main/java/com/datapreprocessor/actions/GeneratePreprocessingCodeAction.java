package com.datapreprocessor.actions;

import com.datapreprocessor.engine.CodeGenerator;
import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;
import com.datapreprocessor.toolwindow.DataPreprocessorToolWindow;
import com.datapreprocessor.toolwindow.DataPreprocessorToolWindowFactory;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inserts the generated pandas preprocessing code at the current editor caret.
 *
 * <p>Requires:
 * <ol>
 *   <li>A dataset to be loaded in the Data Preprocessor tool window.</li>
 *   <li>An open editor file (any .py file is ideal).</li>
 * </ol>
 * </p>
 */
public class GeneratePreprocessingCodeAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Enable only when there is an active editor
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabled(editor != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor  editor  = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) return;

        // Retrieve the current steps from the tool window
        DataPreprocessorToolWindow panel = DataPreprocessorToolWindowFactory.getPanel(project);
        if (panel == null || panel.getCurrentDataSet() == null) {
            Messages.showInfoMessage(
                    project,
                    "Please load a data file (CSV, Excel, or JSON) in the Data Preprocessor tool window first.",
                    "Data Preprocessor");
            return;
        }

        List<PreprocessingStep> steps = panel.getSelectedSteps();
        String filePath = panel.getCurrentDataSet().getFilePath();

        String code = new CodeGenerator().generate(filePath, steps);

        // Insert at caret position
        Document doc = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();

        WriteCommandAction.runWriteCommandAction(project, "Insert Preprocessing Code", null, () ->
            doc.insertString(offset, "\n" + code + "\n"));
    }
}
