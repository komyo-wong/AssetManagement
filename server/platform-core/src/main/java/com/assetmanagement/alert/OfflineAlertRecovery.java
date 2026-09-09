package com.assetmanagement.alert;

/**
 * Offline tickets should clear as soon as the console would show the device
 * online again, not only when the rule's own (possibly shorter) TTL agrees.
 */
public final class OfflineAlertRecovery {

    private OfflineAlertRecovery() {
    }

    public static boolean shouldAutoResolve(boolean onlineByPresence, boolean onlineByRule) {
        return onlineByPresence || onlineByRule;
    }
}
