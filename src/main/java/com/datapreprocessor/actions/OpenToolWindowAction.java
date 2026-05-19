package com.datapreprocessor.actions;

import com.datapreprocessor.toolwindow.DataPreprocessorToolWindowFactory;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the Data Preprocessor tool window from the Tools menu.
 */
public class OpenToolWindowAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(DataPreprocessorToolWindowFactory.TOOL_WINDOW_ID);

        if (toolWindow == null) {
            Messages.showErrorDialog(
                    project,
                    "Data Preprocessor tool window is not registered for this project.",
                    "Data Preprocessor");
            return;
        }

        toolWindow.activate(null);
    }
}
