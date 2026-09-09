package com.assetmanagement.mqtt.inbox;

import com.assetmanagement.mqtt.domain.MqttInboxMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttRecentInboxRecordTest {

    @Test
    void onlyFailedStatusesArePersisted() {
        assertTrue(MqttRecentInboxRecord.shouldPersist("UNSUPPORTED"));
        assertTrue(MqttRecentInboxRecord.shouldPersist("failed"));
        assertFalse(MqttRecentInboxRecord.shouldPersist("PARSED"));
        assertFalse(MqttRecentInboxRecord.shouldPersist("PENDING"));
        assertFalse(MqttRecentInboxRecord.shouldPersist(null));
    }

    @Test
    void unsupportedIsShownAsRejected() {
        assertEquals("rejected", MqttRecentInboxRecord.viewStatus("UNSUPPORTED"));
        assertEquals("parsed", MqttRecentInboxRecord.viewStatus("PARSED"));
        assertTrue(MqttRecentInboxRecord.statusMatches("UNSUPPORTED", "rejected"));
        assertTrue(MqttRecentInboxRecord.statusMatches("PARSED", "parsed"));
        assertFalse(MqttRecentInboxRecord.statusMatches("PARSED", "failed"));
    }

    @Test
    void encodeRoundTripAndFilter() {
        UUID projectId = UUID.randomUUID();
        MqttInboxMessage inbox = new MqttInboxMessage(
                UUID.randomUUID(), projectId, null, "GwData", 0, false, "{\"gw_addr\":\"aabbccddeeff\"}", "a".repeat(64), "k");
        inbox.markParsed("scan_report", null);
        MqttRecentInboxRecord record = MqttRecentInboxRecord.from(inbox, "aabbccddeeff");
        MqttRecentInboxRecord decoded = MqttRecentInboxStore.decode(MqttRecentInboxStore.encode(record));
        assertEquals("GwData", decoded.topic());
        assertEquals("aabbccddeeff", decoded.gatewayMac());
        assertEquals("PARSED", decoded.parseStatus());

        List<MqttRecentInboxRecord> filtered = MqttRecentInboxStore.filter(
                List.of(record), "parsed", "aabbccddeeff", "gw");
        assertEquals(1, filtered.size());
        assertTrue(MqttRecentInboxStore.page(filtered, 2, 20).isEmpty());
    }

    @Test
    void truncatesHugePayload() {
        String huge = "x".repeat(MqttRecentInboxRecord.PAYLOAD_LIMIT + 20);
        MqttInboxMessage inbox = new MqttInboxMessage(
                UUID.randomUUID(), UUID.randomUUID(), null, "t", 0, false, huge, "b".repeat(64), "k2");
        inbox.markUnsupported(null, "no parser");
        MqttRecentInboxRecord record = MqttRecentInboxRecord.from(inbox, "");
        assertTrue(record.payload().endsWith("…(truncated)"));
        assertTrue(record.payload().length() < huge.length());
        Instant now = Instant.now();
        assertTrue(record.receivedAt().isBefore(now.plusSeconds(2)));
    }
}
