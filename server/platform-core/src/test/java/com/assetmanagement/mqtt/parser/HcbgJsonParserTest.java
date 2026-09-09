package com.assetmanagement.mqtt.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HcbgJsonParserTest {

    @Test
    void parsesScanReportWithMultipleDevicesFromProtocolSample() {
        String payload = """
                {
                  "pkt_type":"scan_report",
                  "gw_addr":"f3bd12dc3b6d",
                  "time":"2021-08-01 09:08:06",
                  "data":{
                    "flags":7,
                    "report_type":"adv_srp",
                    "pktSN":31,
                    "pkt_total":2,
                    "pkt_index":1,
                    "dev_total":1000,
                    "dev_num":2,
                    "dev_infos": [
                      {
                        "addr":"f123bcd3deda",
                        "rssi":-61,
                        "time":"2021-08-01 09:08:05",
                        "name":"kbeacon",
                        "ibcn_uuid":"e2c56db5-dffb-48d2-b060-d0f5a7109608",
                        "ibcn_major":16160,
                        "ibcn_minor":35211,
                        "ibcn_rssi_at_1m":-61,
                        "adv_raw":"1234",
                        "srp_raw":"2345"
                      },
                      {
                        "addr":"f123bcd3dedb",
                        "rssi":-62,
                        "name":"kbeacon",
                        "time":"2021-08-01 09:08:05",
                        "adv_raw":"1234",
                        "srp_raw":"2345"
                      }
                    ]
                  }
                }
                """;
        var parsed = HcbgJsonParser.tryParse(payload).orElseThrow();
        assertTrue(parsed.isScanReport());
        assertEquals("adv_srp", parsed.reportType());
        assertEquals("f3bd12dc3b6d", parsed.gatewayMac());
        assertEquals(31, parsed.sequence());
        assertEquals(2, parsed.devices().size());
        assertEquals("f123bcd3deda", parsed.devices().get(0).addr());
        assertEquals(-61, parsed.devices().get(0).rssi());
        assertEquals("kbeacon", parsed.devices().get(0).name());
        assertEquals("e2c56db5-dffb-48d2-b060-d0f5a7109608", parsed.devices().get(0).ibcnUuid());
        assertEquals(16160, parsed.devices().get(0).ibcnMajor());
        assertEquals(35211, parsed.devices().get(0).ibcnMinor());
        assertEquals(-61, parsed.devices().get(0).ibcnRssiAt1m());
        assertEquals("1234", parsed.devices().get(0).advRaw());
        assertEquals("2345", parsed.devices().get(0).srpRaw());
        assertEquals("f123bcd3dedb", parsed.devices().get(1).addr());
        assertFalse(parsed.heartbeat());
        assertTrue(AdvSrpParser.tryParse(payload).isEmpty());
        assertTrue(GwStatusParser.tryParse(payload).isEmpty());
    }

    @Test
    void treatsNullDeviceNameAsMissing() {
        String payload = """
                {"pkt_type":"scan_report","gw_addr":"aabbccddeeff","data":{"report_type":"mac_rssi_only","dev_infos":[{"addr":"112233445566","rssi":-70,"name":"null"}]}}
                """;
        var device = HcbgJsonParser.tryParse(payload).orElseThrow().devices().getFirst();
        assertEquals("112233445566", device.addr());
        assertNull(device.name());
    }

    @Test
    void parsesIbcnsBattReportType() {
        String payload = """
                {
                  "pkt_type":"scan_report",
                  "gw_addr":"f3bd12dc3b6d",
                  "data":{
                    "report_type":"ibcns_batt",
                    "dev_infos":[
                      {"addr":"f123bcd3deda","rssi":-61,"time":"2021-08-01 09:08:05","batt_type":0,"batt_value":85},
                      {"addr":"f123bcd3dedb","rssi":-70,"batt_type":1,"batt_value":3300}
                    ]
                  }
                }
                """;
        var parsed = HcbgJsonParser.tryParse(payload).orElseThrow();
        assertEquals("ibcns_batt", parsed.reportType());
        assertEquals(2, parsed.devices().size());
        assertTrue(parsed.devices().get(0).hasBattery());
        assertEquals(0, parsed.devices().get(0).battType());
        assertEquals(85, parsed.devices().get(0).battValue());
        assertEquals(1, parsed.devices().get(1).battType());
        assertEquals(3300, parsed.devices().get(1).battValue());
    }

    @Test
    void parsesStaGwHbAsHeartbeat() {
        String payload = """
                {
                  "pkt_type":"state",
                  "gw_addr":"f3bd12dc3b6d",
                  "time":"2021-08-01 09:08:06",
                  "data":{"msgId":1234,"state":"sta_gw_hb","flags":3,"ticks_cnt":20}
                }
                """;
        var parsed = HcbgJsonParser.tryParse(payload).orElseThrow();
        assertTrue(parsed.heartbeat());
        assertEquals("state", parsed.pktType());
        assertEquals("f3bd12dc3b6d", parsed.gatewayMac());
        assertTrue(parsed.devices().isEmpty());
    }

    @Test
    void parsesProtocolSampleStateGwHbAlias() {
        String payload = """
                {
                  "pkt_type":"state",
                  "gw_addr":"f3bd12dc3b6d",
                  "time":"2021-08-01 09:08:06",
                  "data":{"msgId":1234,"state":"state_gw_hb","result":true,"reason":0}
                }
                """;
        var parsed = HcbgJsonParser.tryParse(payload).orElseThrow();
        assertTrue(parsed.heartbeat());
        assertEquals("f3bd12dc3b6d", parsed.gatewayMac());
        assertTrue(GwStatusParser.tryParse(payload).isEmpty());
    }

    @Test
    void parsesDiscoveryCompletedWithCharacteristicHandle() {
        String payload = """
                {
                  "pkt_type":"state",
                  "gw_addr":"f3bd12dc3b6d",
                  "data":{
                    "msgId":9,
                    "state":"sta_discovery_state",
                    "device_addr":"f123bcd3deda",
                    "disc_state":"completed",
                    "chars_num":1,
                    "chars":[{"handle":27,"cccd":0}]
                  }
                }
                """;
        var event = HcbgJsonParser.tryParseDiscovery(payload).orElseThrow();
        assertTrue(event.completed());
        assertFalse(event.timedOut());
        assertEquals("f3bd12dc3b6d", event.gatewayMac());
        assertEquals("f123bcd3deda", event.deviceMac());
        assertEquals(27, event.firstHandle());
        assertEquals(1, event.handles().size());
    }

    @Test
    void parsesDeviceDataStatusByte() {
        String payload = """
                {
                  "pkt_type":"state",
                  "gw_addr":"f3bd12dc3b6d",
                  "data":{
                    "state":"sta_device_data",
                    "device_addr":"f123bcd3deda",
                    "recv_infos":{"handle":18,"raw":"11"}
                  }
                }
                """;
        var event = HcbgJsonParser.tryParseDeviceData(payload).orElseThrow();
        assertEquals(18, event.handle());
        assertEquals("11", event.rawHex());
    }

    @Test
    void parsesConnectedDeviceStateAsReadyForDiscovery() {
        String payload = """
                {
                  "pkt_type":"state",
                  "gw_addr":"f3bd12dc3b6d",
                  "data":{
                    "state":"sta_device_state",
                    "device_addr":"f123bcd3deda",
                    "device_state":"ready"
                  }
                }
                """;
        var event = HcbgJsonParser.tryParseConnState(payload).orElseThrow();
        assertTrue(event.readyForDiscovery());
        assertEquals("f3bd12dc3b6d", event.gatewayMac());
        assertEquals("f123bcd3deda", event.deviceMac());
    }

    @Test
    void parsesStaDataSentWhenSendCacheFlushed() {
        String payload = """
                {
                  "pkt_type":"state",
                  "gw_addr":"f3bd12dc3b6d",
                  "data":{
                    "state":"sta_data_sent",
                    "target":"device",
                    "addr":"f123bcd3deda",
                    "data_sent":1,
                    "rest_space":511
                  }
                }
                """;
        var event = HcbgJsonParser.tryParseDataSent(payload).orElseThrow();
        assertTrue(event.toDevice());
        assertEquals("f3bd12dc3b6d", event.gatewayMac());
        assertEquals("f123bcd3deda", event.addr());
        assertEquals(1, event.dataSent());
    }

    @Test
    void ignoresPayloadsWithoutPktType() {
        assertTrue(HcbgJsonParser.tryParse("{\"cmd\":\"inventory_start\",\"gw_mac\":\"aabbccddeeff\"}").isEmpty());
        assertTrue(HcbgJsonParser.tryParse("网关：aabbccddeeff,报文类型:adv_srp").isEmpty());
        assertTrue(HcbgJsonParser.tryParse("").isEmpty());
    }
}
