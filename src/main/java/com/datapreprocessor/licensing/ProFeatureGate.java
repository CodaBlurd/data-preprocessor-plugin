package com.datapreprocessor.licensing;

import com.intellij.ui.LicensingFacade;

/**
 * Central gate for Pro-only functionality.
 */
public final class ProFeatureGate {
    /**
     * Must match the Product Code registered with JetBrains Marketplace and the
     * product-descriptor entry in plugin.xml.
     */
    public static final String PRODUCT_CODE = "PDATAPREPROCESS";

    private static final String KEY_PREFIX = "key:";
    private static final String STAMP_PREFIX = "stamp:";
    private static final String EVAL_PREFIX = "eval:";

    private ProFeatureGate() {}

    public static boolean isUnlocked(ProFeature feature) {
        LicensingFacade facade = LicensingFacade.getInstance();
        if (facade == null) {
            return false;
        }

        return isLicensedConfirmationStamp(facade.getConfirmationStamp(PRODUCT_CODE));
    }

    public static String lockedMessage(ProFeature feature) {
        return feature.label() + " is available in Data Preprocessor Pro.";
    }

    public static String unlockHint() {
        return "Start a trial or activate a Data Preprocessor Pro license through JetBrains Marketplace.";
    }

    static boolean isLicensedConfirmationStamp(String stamp) {
        if (stamp == null || stamp.isBlank()) {
            return false;
        }

        if (stamp.startsWith(KEY_PREFIX) || stamp.startsWith(STAMP_PREFIX)) {
            return true;
        }

        if (stamp.startsWith(EVAL_PREFIX)) {
            return isActiveTrial(stamp.substring(EVAL_PREFIX.length()));
        }

        return false;
    }

    private static boolean isActiveTrial(String expiresAtMillis) {
        try {
            return Long.parseLong(expiresAtMillis) > System.currentTimeMillis();
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
