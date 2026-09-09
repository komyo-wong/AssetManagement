package com.assetmanagement.mqtt.worker.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoalescingInboundDispatcherTest {

    @Test
    void coalescesHcbgScanReportsPerGatewayAndPktIndex() {
        String a = """
                {"pkt_type":"scan_report","gw_addr":"f696ee5b7d63","data":{"pkt_sn":1,"pkt_index":1}}
                """;
        String b = """
                {"pkt_type":"scan_report","gw_addr":"f696ee5b7d63","data":{"pkt_sn":2,"pkt_index":1}}
                """;
        String c = """
                {"pkt_type":"scan_report","gw_addr":"f696ee5b7d63","data":{"pkt_sn":1,"pkt_index":2}}
                """;
        assertEquals(
                CoalescingInboundDispatcher.hcbgCoalesceKey("p", "GwData", a),
                CoalescingInboundDispatcher.hcbgCoalesceKey("p", "GwData", b)
        );
        assertNotEquals(
                CoalescingInboundDispatcher.hcbgCoalesceKey("p", "GwData", a),
                CoalescingInboundDispatcher.hcbgCoalesceKey("p", "GwData", c)
        );
    }

    @Test
    void keepsGattStateEventsUnique() {
        String a = """
                {"pkt_type":"state","gw_addr":"f696ee5b7d63","data":{"state":"sta_discovery_state","disc_state":"completed"}}
                """;
        String b = """
                {"pkt_type":"state","gw_addr":"f696ee5b7d63","data":{"state":"sta_discovery_state","disc_state":"completed"}}
                """;
        assertNotEquals(
                CoalescingInboundDispatcher.hcbgCoalesceKey("p", "GwData", a),
                CoalescingInboundDispatcher.hcbgCoalesceKey("p", "GwData", b)
        );
        assertTrue(CoalescingInboundDispatcher.hcbgCoalesceKey("p", "GwData", a).contains("hcbg-state"));
    }
}
