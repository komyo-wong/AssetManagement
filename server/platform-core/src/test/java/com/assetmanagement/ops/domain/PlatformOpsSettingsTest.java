package com.assetmanagement.ops.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformOpsSettingsTest {

    @Test
    void defaultsEnableDailyCleanupWithRetentionRules() {
        PlatformOpsSettings settings = PlatformOpsSettings.defaults();
        assertTrue(settings.isAutoCleanupEnabled());
        assertEquals(1, settings.getAutoCleanupDays());
        assertTrue(settings.getCleanupRulesJson().contains("\"mqttInbox\""));
        assertTrue(settings.getCleanupRulesJson().contains("\"days\":3"));
        assertTrue(settings.getCleanupRulesJson().contains("\"scanEvents\",\"days\":2"));
        assertTrue(settings.getCleanupRulesJson().contains("\"auditLogs\""));
    }
}
