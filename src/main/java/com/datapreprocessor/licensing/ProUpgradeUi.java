package com.datapreprocessor.licensing;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public final class ProUpgradeUi {
    public static final String PRICING_URL =
            "https://plugins.jetbrains.com/plugin/31226-data-preprocessor/pricing";

    private ProUpgradeUi() {}

    public static void openPricingPage() {
        BrowserUtil.browse(PRICING_URL);
    }

    public static void showLockedDialog(Project project, ProFeature feature) {
        String message = ProFeatureGate.lockedMessage(feature)
                + "\n\n"
                + ProFeatureGate.unlockHint();
        int choice = Messages.showDialog(
                project,
                message,
                "Data Preprocessor Pro",
                new String[]{"Upgrade to Pro", "Not Now"},
                0,
                Messages.getInformationIcon()
        );
        if (choice == 0) {
            openPricingPage();
        }
    }
}
