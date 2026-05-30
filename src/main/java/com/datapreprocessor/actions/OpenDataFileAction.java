package com.datapreprocessor.actions;

import com.datapreprocessor.engine.DataCleaner;
import com.datapreprocessor.engine.DataLoader;
import com.datapreprocessor.model.ColumnProfile;
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
import java.util.List;

/**
 * Action triggered from the Project View context menu or the Tools menu.
 *
 * <p>If the selected file is a CSV, Excel (.xlsx), or JSON file it is loaded
 * into the Data Preprocessor tool window. Otherwise the action is hidden.</p>
 */
public class OpenDataFileAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // VirtualFile is a slow data key — must be resolved on BGT, not EDT.
        // The platform pre-caches it before calling update(), so BGT is safe.
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (file == null || file.isDirectory()) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        String ext = file.getExtension();
        boolean isSupported = "csv".equalsIgnoreCase(ext)
                || "xlsx".equalsIgnoreCase(ext)
                || "json".equalsIgnoreCase(ext);

        e.getPresentation().setEnabledAndVisible(isSupported);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null
                || (!"csv".equalsIgnoreCase(file.getExtension())
                && !"xlsx".equalsIgnoreCase(file.getExtension())
                && !"json".equalsIgnoreCase(file.getExtension()))) {
            Messages.showInfoMessage(
                    project,
                    "Please select a CSV, Excel (.xlsx), or JSON file.",
                    "Data Preprocessor");
            return;
        }

        // Load the dataset AND compute column profiles on a background thread.
        // DataCleaner.profileColumns() is O(n·columns) and must not block the EDT.
        SwingWorker<LoadResult, Void> worker = new SwingWorker<>() {

            @Override
            protected LoadResult doInBackground() throws IOException {
                DataSet ds = new DataLoader().load(file.getPath());
                List<ColumnProfile> profiles = new DataCleaner().profileColumns(ds);
                return new LoadResult(ds, profiles);
            }

            @Override
            protected void done() {
                try {
                    LoadResult result = get();
                    openToolWindowWithData(project, result.dataSet(), result.profiles());
                } catch (Exception ex) {
                    Messages.showErrorDialog(
                            project,
                            "Failed to load file:\n" + ex.getMessage(),
                            "Data Preprocessor – Load Error");
                }
            }
        };
        worker.execute();
    }

    // -------------------------------------------------------------------------

    /** Pairs a freshly loaded dataset with its pre-computed column profiles. */
    private record LoadResult(DataSet dataSet, List<ColumnProfile> profiles) {}

    private void openToolWindowWithData(Project project, DataSet ds, List<ColumnProfile> profiles) {
        ToolWindowManager twm = ToolWindowManager.getInstance(project);
        ToolWindow tw = twm.getToolWindow(DataPreprocessorToolWindowFactory.TOOL_WINDOW_ID);
        if (tw == null) return;

        tw.activate(() -> {
            DataPreprocessorToolWindow panel = DataPreprocessorToolWindowFactory.getPanel(project);
            if (panel != null) panel.loadDataSet(ds, profiles);
        });
    }
}
