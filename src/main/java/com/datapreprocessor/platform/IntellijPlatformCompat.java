package com.datapreprocessor.platform;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Small compatibility bridge for IntelliJ Platform APIs whose preferred entry
 * points changed across the plugin's supported IDE range.
 */
public final class IntellijPlatformCompat {

    private IntellijPlatformCompat() {}

    public static FileSaverDescriptor saveDescriptor(String title, String description) {
        try {
            Constructor<FileSaverDescriptor> current =
                    FileSaverDescriptor.class.getConstructor(String.class, String.class);
            return current.newInstance(title, description);
        } catch (NoSuchMethodException ignored) {
            return legacySaveDescriptor(title, description);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create file saver descriptor", e);
        }
    }

    public static FileChooserDescriptor singleFileDescriptor(String extension) {
        return new FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter(file -> {
                    if (file.isDirectory()) return true;
                    return extension.equalsIgnoreCase(file.getExtension());
                });
    }

    private static FileSaverDescriptor legacySaveDescriptor(String title, String description) {
        try {
            Constructor<FileSaverDescriptor> legacy =
                    FileSaverDescriptor.class.getConstructor(String.class, String.class, String[].class);
            return legacy.newInstance(title, description, new String[0]);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create legacy file saver descriptor", e);
        }
    }

    public static void performAction(AnAction action, AnActionEvent event) {
        try {
            Class<?> actionUtil = Class.forName("com.intellij.openapi.actionSystem.ex.ActionUtil");
            Method performAction = actionUtil.getMethod("performAction", AnAction.class, AnActionEvent.class);
            performAction.invoke(null, action, event);
            return;
        } catch (NoSuchMethodException ignored) {
            invokeLegacyAction(action, event);
            return;
        } catch (ReflectiveOperationException ignored) {
            ActionManager.getInstance().tryToExecute(action, null, null, ActionPlaces.UNKNOWN, true);
        }
    }

    private static void invokeLegacyAction(AnAction action, AnActionEvent event) {
        try {
            Class<?> actionUtil = Class.forName("com.intellij.openapi.actionSystem.ex.ActionUtil");
            Method invokeAction = actionUtil.getMethod("invokeAction", AnAction.class, AnActionEvent.class, Runnable.class);
            invokeAction.invoke(null, action, event, null);
        } catch (ReflectiveOperationException ignored) {
            ActionManager.getInstance().tryToExecute(action, null, null, ActionPlaces.UNKNOWN, true);
        }
    }
}
