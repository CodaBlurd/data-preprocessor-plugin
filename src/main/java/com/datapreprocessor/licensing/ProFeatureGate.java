package com.datapreprocessor.licensing;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.ui.LicensingFacade;

import java.util.Date;

/**
 * Central gate for Pro-only functionality.
 *
 * <p>Android Studio is built on IntelliJ Community code but is a Google product —
 * it never initialises JetBrains' licensing subsystem, so {@link LicensingFacade#getInstance()}
 * always returns {@code null} there and the "Register" IDE action does not exist.
 * Pro features are therefore unlocked unconditionally in Android Studio so that
 * Android Studio users are not permanently locked out with no upgrade path.</p>
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
        if (isAndroidStudio()) return true;
        LicensingFacade facade = LicensingFacade.getInstance();
        return isUnlocked(facade);
    }

    /**
     * Returns {@code true} when running inside Android Studio.
     * Android Studio does not initialise JetBrains' {@link LicensingFacade}, so
     * paid-plugin licensing is unavailable there regardless of any purchase.
     */
    static boolean isAndroidStudio() {
        try {
            String name = ApplicationInfo.getInstance().getFullApplicationName();
            return name != null && name.toLowerCase().contains("android studio");
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isUnlocked(LicensingFacade facade) {
        if (facade == null) {
            return false;
        }

        if (isLicensedConfirmationStamp(facade.getConfirmationStamp(PRODUCT_CODE))) {
            return true;
        }

        if (facade.productLicenses != null) {
            LicensingFacade.ProductLicenseData license = facade.productLicenses.get(PRODUCT_CODE);
            if (license != null) {
                return isLicensedConfirmationStamp(license.confirmationStamp)
                        || isActiveExpirationDate(license.expirationDate);
            }
        }

        return isActiveExpirationDate(facade.getExpirationDate(PRODUCT_CODE));
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

    private static boolean isActiveExpirationDate(Date expirationDate) {
        return expirationDate != null && expirationDate.getTime() > System.currentTimeMillis();
    }
}
