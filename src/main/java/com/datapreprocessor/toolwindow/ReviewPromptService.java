package com.datapreprocessor.toolwindow;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

/**
 * Tracks how many times a CSV file has been loaded into the plugin and
 * shows a one-time balloon notification asking for a Marketplace review
 * after {@link #PROMPT_AFTER} uses.
 *
 * <p>State is stored application-wide via {@link PropertiesComponent} so the
 * count persists across IDE restarts and is not reset per project.</p>
 */
public final class ReviewPromptService {

    private static final String KEY_USE_COUNT = "com.datapreprocessor.useCount";
    private static final String KEY_PROMPTED  = "com.datapreprocessor.reviewPrompted";
    private static final int    PROMPT_AFTER  = 3;

    private static final String REVIEW_URL =
            "https://plugins.jetbrains.com/plugin/31226-data-preprocessor/reviews";

    private ReviewPromptService() {}

    /**
     * Call this every time a dataset is successfully loaded.
     * Increments the persistent use counter and fires the review prompt
     * exactly once when the counter reaches {@link #PROMPT_AFTER}.
     */
    public static void recordUseAndMaybePrompt(Project project) {
        PropertiesComponent props = PropertiesComponent.getInstance();

        // Never show the prompt more than once
        if (props.getBoolean(KEY_PROMPTED, false)) return;

        int count = props.getInt(KEY_USE_COUNT, 0) + 1;
        props.setValue(KEY_USE_COUNT, count, 0);

        if (count >= PROMPT_AFTER) {
            props.setValue(KEY_PROMPTED, true);
            showReviewNotification(project);
        }
    }

    private static void showReviewNotification(Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("DataPreprocessor.Notifications")
                .createNotification(
                        "Enjoying Data Preprocessor?",
                        "If it's saving you time, a quick review on JetBrains Marketplace " +
                        "really helps \uD83D\uDE4F",   // 🙏
                        NotificationType.INFORMATION
                )
                .addAction(NotificationAction.createSimple(
                        "Leave a Review \u2192",        // →
                        () -> BrowserUtil.browse(REVIEW_URL)
                ))
                .addAction(NotificationAction.createSimple(
                        "Maybe Later",
                        () -> { /* dismiss — flag already set, won't re-appear */ }
                ))
                .notify(project);
    }
}
