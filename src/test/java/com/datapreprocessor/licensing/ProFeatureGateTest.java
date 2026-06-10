package com.datapreprocessor.licensing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProFeatureGateTest {

    @Test
    void doesNotUseLocalPropertyOverride() {
        try {
            System.setProperty("datapreprocessor.pro.enabled", "true");
            assertFalse(ProFeatureGate.isUnlocked(ProFeature.BATCH_PROCESSING));
        } finally {
            System.clearProperty("datapreprocessor.pro.enabled");
        }
    }

    @Test
    void acceptsMarketplaceLicenseAndFloatingServerStamps() {
        assertTrue(ProFeatureGate.isLicensedConfirmationStamp("key:license"));
        assertTrue(ProFeatureGate.isLicensedConfirmationStamp("stamp:server"));
    }

    @Test
    void acceptsOnlyActiveMarketplaceTrials() {
        long future = System.currentTimeMillis() + 60_000;
        long past = System.currentTimeMillis() - 60_000;

        assertTrue(ProFeatureGate.isLicensedConfirmationStamp("eval:" + future));
        assertFalse(ProFeatureGate.isLicensedConfirmationStamp("eval:" + past));
        assertFalse(ProFeatureGate.isLicensedConfirmationStamp("eval:not-a-time"));
    }

    @Test
    void rejectsMissingOrUnknownMarketplaceStamps() {
        assertFalse(ProFeatureGate.isLicensedConfirmationStamp(null));
        assertFalse(ProFeatureGate.isLicensedConfirmationStamp(""));
        assertFalse(ProFeatureGate.isLicensedConfirmationStamp("unknown:value"));
    }
}
