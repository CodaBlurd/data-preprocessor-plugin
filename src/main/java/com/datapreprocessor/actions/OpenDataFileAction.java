package com.datapreprocessor.actions;

import com.datapreprocessor.engine.DataLoader;
import com.datapreprocessor.model.DataSet;
import com.datapreprocessor.toolwindow.DataPreprocessorToolWindow;
import com.datapreprocessor.toolwindow.DataPreprocessorToolWindowFactory;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;

/**
 * Action triggered from the Project View context menu or the Tools menu.
 *
 * <p>If the selected file is a CSV it is loaded into the Data Preprocessor
 * tool window. Otherwise an informational dialog is shown.</p>
 */
public class OpenDataFileAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // We read from the VirtualFile selection — must run on EDT
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Show the action only when exactly one CSV file is selected
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean isCsv = file != null
                && !file.isDirectory()
                && "csv".equalsIgnoreCase(file.getExtension());
        e.getPresentation().setEnabledAndVisible(isCsv);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null || !"csv".equalsIgnoreCase(file.getExtension())) {
            Messages.showInfoMessage(
                    project,
                    "Please select a CSV file in the Project view.",
                    "Data Preprocessor");
            return;
        }

        // Load the dataset on a background thread, then update the UI on EDT
        SwingWorker<DataSet, Void> worker = new SwingWorker<>() {

            @Override
            protected DataSet doInBackground() throws IOException {
                return new DataLoader().loadCsv(file.getPath());
            }

            @Override
            protected void done() {
                try {
                    DataSet ds = get();
                    openToolWindowWithData(project, ds);
                } catch (Exception ex) {
                    Messages.showErrorDialog(
                            project,
                            "Failed to load CSV file:\n" + ex.getMessage(),
                            "Data Preprocessor – Load Error");
                }
            }
        };
        worker.execute();
    }

    // -------------------------------------------------------------------------

    private void openToolWindowWithData(Project project, DataSet ds) {
        ToolWindowManager twm = ToolWindowManager.getInstance(project);
        ToolWindow tw = twm.getToolWindow(DataPreprocessorToolWindowFactory.TOOL_WINDOW_ID);
        if (tw == null) return;

        tw.activate(() -> {
            // Retrieve our panel and load the dataset
            DataPreprocessorToolWindow panel = DataPreprocessorToolWindowFactory.getPanel(project);
            if (panel != null) panel.loadDataSet(ds);
        });
    }
}
