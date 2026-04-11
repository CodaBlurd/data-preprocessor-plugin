package com.datapreprocessor.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the "Data Preprocessor" tool window and creates one
 * {@link DataPreprocessorToolWindow} panel per open project.
 */
public class DataPreprocessorToolWindowFactory implements ToolWindowFactory {

    public static final String TOOL_WINDOW_ID = "Data Preprocessor";

    /** Holds the panel per project so actions can retrieve it. */
    private static final Map<Project, DataPreprocessorToolWindow> PANELS
            = new ConcurrentHashMap<>();

    @Override
    public void createToolWindowContent(@NotNull Project project,
                                        @NotNull ToolWindow toolWindow) {

        DataPreprocessorToolWindow panel = new DataPreprocessorToolWindow(project);
        PANELS.put(project, panel);

        ContentFactory cf      = ContentFactory.getInstance();
        Content        content = cf.createContent(panel.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);

        // Clean up when the tool window (and its project) is disposed.
        // This replaces the removed ProjectManagerListener.TOPIC API.
        Disposer.register(toolWindow.getDisposable(), () -> PANELS.remove(project));
    }

    /**
     * Returns the live panel for the given project, or {@code null} if the
     * tool window has not been initialised yet.
     */
    public static DataPreprocessorToolWindow getPanel(Project project) {
        return PANELS.get(project);
    }
}