package com.assetmanagement.ops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformCleanupJobStateTest {

    @Test
    void tracksRunningThenSucceeded() {
        PlatformCleanupJobState state = new PlatformCleanupJobState();
        assertEquals("idle", state.get().status());
        state.start(List.of("mqttInbox"));
        assertEquals("running", state.get().status());
        state.progress("mqtt_inbox", 12, List.of(Map.of("id", "mqttInbox", "deleted", 12)));
        state.succeed(12, List.of(Map.of("id", "mqttInbox", "deleted", 12)));
        Map<String, Object> map = state.toMap();
        assertEquals("succeeded", map.get("status"));
        assertEquals(12L, map.get("deleted"));
        assertTrue(map.get("items") instanceof List<?>);
    }
}
