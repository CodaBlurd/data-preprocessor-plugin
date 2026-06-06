package com.datapreprocessor.toolwindow;

import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;
import com.datapreprocessor.engine.PipelineSerializer;
import com.datapreprocessor.engine.PipelineValidator;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;

import javax.swing.SwingWorker;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

class PipelineFileActions {
    private final Project project;
    private final Supplier<String> getSourcePath;
    private final Consumer<String> onStatus;
    private final PipelineSerializer serializer;
    private final PipelineValidator validator;

    PipelineFileActions(Project project, Supplier<String> getSourcePath, Consumer<String> onStatus) {
        this(project, getSourcePath, onStatus, new PipelineSerializer(), new PipelineValidator());
    }

    PipelineFileActions(Project project,
                        Supplier<String> getSourcePath,
                        Consumer<String> onStatus,
                        PipelineSerializer serializer,
                        PipelineValidator validator) {
        this.project = project;
        this.getSourcePath = getSourcePath;
        this.onStatus = onStatus;
        this.serializer = serializer;
        this.validator = validator;
    }

    void exportPipeline(List<PreprocessingStep> steps) {
        if (steps.isEmpty()) {
            onStatus.accept("No pipeline steps to export.");
            return;
        }

        List<PreprocessingStep> stepsSnapshot = new ArrayList<>(steps);
        FileSaverDescriptor descriptor = new FileSaverDescriptor(
                "Export Data Preprocessor Pipeline",
                "Save the current preprocessing pipeline"
        );

        FileSaverDialog dialog = FileChooserFactory.getInstance()
                .createSaveFileDialog(descriptor, project);

        VirtualFileWrapper selected = dialog.save(defaultOutputDirectory(), "pipeline.dpp");
        if (selected == null) {
            onStatus.accept("Pipeline export cancelled.");
            return;
        }

        Path path = selected.getFile().toPath();
        onStatus.accept("Exporting pipeline...");

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                serializer.save(path, stepsSnapshot);
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                    onStatus.accept("Pipeline exported: " + path);
                } catch (Exception ex) {
                    Messages.showErrorDialog(project,
                            "Could not export pipeline:\n" + ex.getMessage(),
                            "Data Preprocessor");
                }
            }
        }.execute();
    }

    void importPipeline(List<String> currentColumnNames, Consumer<List<PreprocessingStep>> onImported) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory
                .createSingleFileDescriptor("dpp")
                .withTitle("Import Data Preprocessor Pipeline")
                .withDescription("Choose a .dpp pipeline file");

        FileChooser.chooseFile(descriptor, project, null, file -> {
            if (file == null) return;

            List<String> columnSnapshot = new ArrayList<>(currentColumnNames);
            onStatus.accept("Importing pipeline...");

            new SwingWorker<ImportedPipeline, Void>() {
                @Override protected ImportedPipeline doInBackground() throws Exception {
                    List<PreprocessingStep> steps = serializer.load(Path.of(file.getPath()));
                    List<String> warnings = validator.validateColumns(steps, columnSnapshot);
                    return new ImportedPipeline(steps, warnings);
                }

                @Override protected void done() {
                    try {
                        ImportedPipeline imported = get();
                        onImported.accept(imported.steps());

                        if (imported.warnings().isEmpty()) {
                            onStatus.accept("Pipeline imported. Total: " + imported.steps().size());
                        } else {
                            onStatus.accept("Pipeline imported with " + imported.warnings().size() + " warning(s).");
                            Messages.showWarningDialog(project,
                                    String.join("\n", imported.warnings()),
                                    "Pipeline Import Warnings");
                        }
                    } catch (Exception ex) {
                        Messages.showErrorDialog(project,
                                "Could not import pipeline:\n" + ex.getMessage(),
                                "Data Preprocessor");
                    }
                }
            }.execute();
        });
    }

    private VirtualFile defaultOutputDirectory() {
        String sourcePath = getSourcePath.get();
        if (sourcePath != null) {
            Path parent = Paths.get(sourcePath).getParent();
            if (parent != null) {
                VirtualFile sourceParent = LocalFileSystem.getInstance().findFileByNioFile(parent);
                if (sourceParent != null) return sourceParent;
            }
        }
        return project.getBaseDir();
    }

    private record ImportedPipeline(List<PreprocessingStep> steps, List<String> warnings) {}
}
