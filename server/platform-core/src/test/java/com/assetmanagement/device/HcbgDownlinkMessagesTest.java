package com.assetmanagement.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HcbgDownlinkMessagesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void scanReportOnOffMatchesProtocolCommandEnvelope() throws Exception {
        String payload = HcbgDownlinkMessages.scanReportOnOff("f3:bd:12:dc:3b:6d", true);
        JsonNode root = MAPPER.readTree(payload);
        assertEquals("command", root.path("pkt_type").asText());
        assertEquals("f3bd12dc3b6d", root.path("gw_addr").asText());
        JsonNode data = root.path("data");
        assertEquals("scan_report_onoff", data.path("cmd").asText());
        assertTrue(data.path("enable").asBoolean());
        int msgId = data.path("msgId").asInt();
        assertTrue(msgId >= 1 && msgId <= 65_534);
    }

    @Test
    void scanRequestOnOffEnablesAdvSrp() throws Exception {
        String payload = HcbgDownlinkMessages.scanRequestOnOff("F3BD12DC3B6D", false);
        JsonNode data = MAPPER.readTree(payload).path("data");
        assertEquals("scan_request_onoff", data.path("cmd").asText());
        assertTrue(data.path("enable").isBoolean());
        assertEquals(false, data.path("enable").asBoolean());
    }

    @Test
    void commandClampsMsgIdToProtocolRange() throws Exception {
        JsonNode data = MAPPER.readTree(
                HcbgDownlinkMessages.command("aabbccddeeff", 0, "scan_report_onoff", "\"enable\":true")
        ).path("data");
        assertEquals(1, data.path("msgId").asInt());

        data = MAPPER.readTree(
                HcbgDownlinkMessages.command("aabbccddeeff", 70_000, "scan_report_onoff", "\"enable\":true")
        ).path("data");
        assertEquals(65_534, data.path("msgId").asInt());
    }

    @Test
    void connAddrRequestAndBuzzerDiscoveryUseProtocolEnvelope() throws Exception {
        JsonNode connect = MAPPER.readTree(HcbgDownlinkMessages.connAddrRequest("f3bd12dc3b6d", "f1:23:bc:d3:de:da", 20));
        assertEquals("command", connect.path("pkt_type").asText());
        assertEquals("conn_addr_request", connect.path("data").path("cmd").asText());
        assertEquals("f123bcd3deda", connect.path("data").path("devices").get(0).path("addr").asText());

        JsonNode discovery = MAPPER.readTree(
                HcbgDownlinkMessages.connTriggerBuzzerDiscovery("f3bd12dc3b6d", "F123BCD3DEDA"));
        assertEquals("conn_trigger_discovery", discovery.path("data").path("cmd").asText());
        assertEquals("0000ff10-0000-1000-8000-00805f9b34fb", discovery.path("data").path("service_uuid").asText());
        assertEquals("0000ff24-0000-1000-8000-00805f9b34fb", discovery.path("data").path("chars_infos").get(0).path("uuid").asText());

        JsonNode on = MAPPER.readTree(
                HcbgDownlinkMessages.connSendData("f3bd12dc3b6d", "f123bcd3deda", 27, "01"));
        assertEquals("conn_send_data", on.path("data").path("cmd").asText());
        assertEquals(1, on.path("data").path("send_num").asInt());
        assertEquals("request", on.path("data").path("send_infos").get(0).path("type").asText());
        assertEquals("01", on.path("data").path("send_infos").get(0).path("raw").asText());
        assertEquals(27, on.path("data").path("send_infos").get(0).path("handle").asInt());
        assertEquals(1, on.path("data").path("send_infos").size());
        assertEquals(90, MAPPER.readTree(
                HcbgDownlinkMessages.connAddrRequest("f3bd12dc3b6d", "f123bcd3deda", 90)
        ).path("data").path("keep_time").asInt());
    }

    @Test
    void connAddrRequestCanCarryWriteOnConnect() throws Exception {
        JsonNode data = MAPPER.readTree(
                HcbgDownlinkMessages.connAddrRequest(
                        "f3bd12dc3b6d", "f123bcd3deda", 90, 48, "01")
        ).path("data");
        assertEquals("conn_addr_request", data.path("cmd").asText());
        assertEquals(1, data.path("send_num").asInt());
        assertEquals(48, data.path("send_infos").get(0).path("handle").asInt());
        assertEquals("request", data.path("send_infos").get(0).path("type").asText());
        assertEquals("01", data.path("send_infos").get(0).path("raw").asText());
        assertEquals(48, HcbgDownlinkMessages.GEOTAG_FF24_WRITE_HANDLE);
    }

    @Test
    void connAddrRequestBuzzerSendsOneChirpOnConnect() throws Exception {
        JsonNode data = MAPPER.readTree(
                HcbgDownlinkMessages.connAddrRequestBuzzer(
                        "f3bd12dc3b6d", "f123bcd3deda", 90, 48, 3, false)
        ).path("data");
        assertEquals("conn_addr_request", data.path("cmd").asText());
        assertEquals(1, data.path("send_num").asInt());
        assertEquals("01", data.path("send_infos").get(0).path("raw").asText());

        JsonNode stop = MAPPER.readTree(
                HcbgDownlinkMessages.connAddrRequestBuzzer(
                        "f3bd12dc3b6d", "f123bcd3deda", 90, 48, 3, true)
        ).path("data");
        assertEquals(1, stop.path("send_num").asInt());
        assertEquals("00", stop.path("send_infos").get(0).path("raw").asText());
    }

    @Test
    void einkDiscoveryRequestsFfe0SessionDataAndUnlock() throws Exception {
        JsonNode discovery = MAPPER.readTree(
                HcbgDownlinkMessages.connTriggerEinkDiscovery("f3bd12dc3b6d", "f123bcd3deda"));
        assertEquals("conn_trigger_discovery", discovery.path("data").path("cmd").asText());
        assertEquals(HcbgDownlinkMessages.EINK_SERVICE_UUID, discovery.path("data").path("service_uuid").asText());
        assertEquals(3, discovery.path("data").path("chars_num").asInt());
        assertEquals(HcbgDownlinkMessages.EINK_SESSION_UUID, discovery.path("data").path("chars_infos").get(0).path("uuid").asText());
    }
}
