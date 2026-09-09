package com.assetmanagement.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformCleanupTargetTest {

    @Test
    void inboxDefaultRetentionIsThreeDays() {
        assertEquals(3, PlatformCleanupTarget.MQTT_INBOX.defaultDays());
        assertEquals(2, PlatformCleanupTarget.SCAN_EVENTS.defaultDays());
        assertEquals(90, PlatformCleanupTarget.AUDIT_LOGS.defaultDays());
    }
}
