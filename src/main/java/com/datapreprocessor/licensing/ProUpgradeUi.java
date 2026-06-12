package com.datapreprocessor.licensing;

import com.datapreprocessor.platform.IntellijPlatformCompat;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

public final class ProUpgradeUi {
    public static final String PRICING_URL =
            "https://plugins.jetbrains.com/plugin/31226-data-preprocessor/pricing";
    private static final String REGISTER_ACTION_ID = "Register";
    private static final DataKey<Boolean> DIRECT_REGISTER_REQUEST_KEY =
            DataKey.create("register.request.direct.call");

    private ProUpgradeUi() {}

    public static void openPricingPage() {
        BrowserUtil.browse(PRICING_URL);
    }

    public static void openLicenseManager(Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            AnAction registerAction = ActionManager.getInstance().getAction(REGISTER_ACTION_ID);
            if (registerAction == null) {
                openPricingPage();
                return;
            }

            DataContext dataContext = SimpleDataContext.builder()
                    .add(DIRECT_REGISTER_REQUEST_KEY, Boolean.TRUE)
                    .add(CommonDataKeys.PROJECT, project)
                    .build();
            IntellijPlatformCompat.performAction(
                    registerAction,
                    AnActionEvent.createEvent(
                                registerAction,
                                dataContext,
                                null,
                                ActionPlaces.UNKNOWN,
                                ActionUiKind.NONE,
                                null
                        )
            );
        });
    }

    public static void showLockedDialog(Project project) {
        openLicenseManager(project);
    }
}
