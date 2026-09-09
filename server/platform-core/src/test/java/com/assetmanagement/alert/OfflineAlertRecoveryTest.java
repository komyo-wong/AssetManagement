package com.assetmanagement.alert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineAlertRecoveryTest {

    @Test
    void recoversWhenConsoleAlreadyShowsOnline() {
        assertTrue(OfflineAlertRecovery.shouldAutoResolve(true, false));
    }

    @Test
    void recoversWhenRuleTtlSaysOnline() {
        assertTrue(OfflineAlertRecovery.shouldAutoResolve(false, true));
    }

    @Test
    void staysOpenWhileBothStillOffline() {
        assertFalse(OfflineAlertRecovery.shouldAutoResolve(false, false));
    }
}
