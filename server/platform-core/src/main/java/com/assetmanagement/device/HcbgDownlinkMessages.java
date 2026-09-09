package com.assetmanagement.device;

import com.assetmanagement.shared.util.MacAddresses;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hivecom HCBG01 JSON downlink ({@code pkt_type=command} on SrvData).
 */
public final class HcbgDownlinkMessages {

    private static final AtomicInteger MSG_ID = new AtomicInteger(ThreadLocalRandom.current().nextInt(1, 60_000));

    private HcbgDownlinkMessages() {
    }

    public static int nextMsgId() {
        return MSG_ID.updateAndGet(current -> current >= 65_534 ? 1 : current + 1);
    }

    /**
     * HCBG discovery uses the protocol 128-bit SIG form. 16-bit {@code ff24} (ESP32)
     * times out on this gateway; 128-bit at least completes and returns a handle.
     */
    public static final String BUZZER_SERVICE_UUID = "0000ff10-0000-1000-8000-00805f9b34fb";
    public static final String BUZZER_CHAR_UUID = "0000ff24-0000-1000-8000-00805f9b34fb";
    public static final String EINK_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static final String EINK_SESSION_UUID = "0000fff1-0000-1000-8000-00805f9b34fb";
    public static final String EINK_DATA_UUID = "0000fff2-0000-1000-8000-00805f9b34fb";
    public static final String EINK_UNLOCK_UUID = "0000ffea-0000-1000-8000-00805f9b34fb";
    /**
     * GeoTag FF24 write handle returned by HCBG {@code conn_trigger_discovery} (0x0030).
     * Vendor docs: same firmware → same handle; bake it into the server and skip live discovery.
     */
    public static final int GEOTAG_FF24_WRITE_HANDLE = 48;
    /**
     * HCBG hex protocol: {@code 0x00} 写命令 / {@code 0x01} 写请求 / {@code 0x02} 读.
     * JSON {@code request} = Write Request. GeoTag FF24 matches ESP32 {@code ble_gattc_write_flat}.
     */
    public static final String SEND_TYPE_WRITE_REQUEST = "request";

    public static String scanReportOnOff(String gatewayMac, boolean enable) {
        return command(gatewayMac, nextMsgId(), "scan_report_onoff", "\"enable\":" + enable);
    }

    /** Request scan-response PDUs so report_type becomes adv_srp. */
    public static String scanRequestOnOff(String gatewayMac, boolean enable) {
        return command(gatewayMac, nextMsgId(), "scan_request_onoff", "\"enable\":" + enable);
    }

    public static String connAddrRequest(String gatewayMac, String deviceMac, int keepTimeSeconds) {
        return connAddrRequest(gatewayMac, deviceMac, keepTimeSeconds, null, 0);
    }

    /**
     * Protocol 3.1.5 example: connect with {@code send_infos} so the gateway writes when
     * {@code device_state=ready}. Live {@code conn_send_data} after discovery never produced
     * {@code sta_data_sent} (send cache never flushed to BLE).
     */
    public static String connAddrRequest(
            String gatewayMac,
            String deviceMac,
            int keepTimeSeconds,
            int writeHandle,
            String hexPayload
    ) {
        return connAddrRequest(gatewayMac, deviceMac, keepTimeSeconds, writeInfo(writeHandle, hexPayload), 1);
    }

    /**
     * First chirp only. Later {@code 01} writes are spaced 2s apart by the follow-up
     * poller — a burst of {@code 01}/{@code 00} is too fast to hear.
     */
    public static String connAddrRequestBuzzer(
            String gatewayMac,
            String deviceMac,
            int keepTimeSeconds,
            int writeHandle,
            int pulseCount,
            boolean stop
    ) {
        String raw = stop ? "00" : "01";
        return connAddrRequest(gatewayMac, deviceMac, keepTimeSeconds, writeHandle, raw);
    }

    public static String buzzerPulseInfos(int writeHandle, int pulseCount) {
        int count = Math.max(1, Math.min(15, pulseCount));
        StringBuilder infos = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                infos.append(',');
            }
            infos.append(writeInfo(writeHandle, "01"));
            infos.append(',');
            infos.append(writeInfo(writeHandle, "00"));
        }
        return infos.toString();
    }

    public static String connAddrRequest(
            String gatewayMac,
            String deviceMac,
            int keepTimeSeconds,
            String sendInfosJson,
            int sendNum
    ) {
        String addr = MacAddresses.compact(deviceMac);
        int keep = Math.max(5, Math.min(180, keepTimeSeconds));
        String extras = "\"devices_num\":1,\"devices\":[{\"addr\":\"" + escape(addr) + "\"}],\"keep_time\":" + keep;
        if (sendInfosJson != null && !sendInfosJson.isBlank() && sendNum > 0) {
            extras += ",\"send_num\":" + sendNum + ",\"send_infos\":[" + sendInfosJson + "]";
        }
        return command(gatewayMac, nextMsgId(), "conn_addr_request", extras);
    }

    public static String connAddrDisconn(String gatewayMac, String deviceMac) {
        String addr = MacAddresses.compact(deviceMac);
        return command(
                gatewayMac,
                nextMsgId(),
                "conn_addr_disconn",
                "\"devices_num\":1,\"devices\":[{\"addr\":\"" + escape(addr) + "\"}]"
        );
    }

    public static String connTriggerBuzzerDiscovery(String gatewayMac, String deviceMac) {
        String addr = MacAddresses.compact(deviceMac);
        return command(
                gatewayMac,
                nextMsgId(),
                "conn_trigger_discovery",
                "\"device_addr\":\"" + escape(addr) + "\""
                        + ",\"service_uuid\":\"" + BUZZER_SERVICE_UUID + "\""
                        + ",\"chars_num\":1"
                        + ",\"chars_infos\":[{\"uuid\":\"" + BUZZER_CHAR_UUID + "\"}]"
        );
    }

    public static String connTriggerEinkDiscovery(String gatewayMac, String deviceMac) {
        String addr = MacAddresses.compact(deviceMac);
        return command(
                gatewayMac,
                nextMsgId(),
                "conn_trigger_discovery",
                "\"device_addr\":\"" + escape(addr) + "\""
                        + ",\"service_uuid\":\"" + EINK_SERVICE_UUID + "\""
                        + ",\"chars_num\":3"
                        + ",\"chars_infos\":["
                        + "{\"uuid\":\"" + EINK_SESSION_UUID + "\"},"
                        + "{\"uuid\":\"" + EINK_DATA_UUID + "\"},"
                        + "{\"uuid\":\"" + EINK_UNLOCK_UUID + "\"}"
                        + "]"
        );
    }

    public static String connReadHandle(String gatewayMac, String deviceMac, int handle) {
        String addr = MacAddresses.compact(deviceMac);
        return command(
                gatewayMac,
                nextMsgId(),
                "conn_read_data",
                "\"devices_num\":1,\"devices\":[{\"addr\":\"" + escape(addr) + "\"}]"
                        + ",\"read_num\":1"
                        + ",\"read_infos\":[{\"handle\":" + handle + "}]"
        );
    }

    public static String connSendWrites(String gatewayMac, String deviceMac, String sendInfosJson, int sendNum) {
        String addr = MacAddresses.compact(deviceMac);
        return command(
                gatewayMac,
                nextMsgId(),
                "conn_send_data",
                "\"devices_num\":1,\"devices\":[{\"addr\":\"" + escape(addr) + "\"}]"
                        + ",\"send_num\":" + sendNum
                        + ",\"send_infos\":[" + sendInfosJson + "]"
        );
    }

    public static String writeInfo(int handle, String hexPayload) {
        return writeInfo(handle, hexPayload, SEND_TYPE_WRITE_REQUEST);
    }

    public static String writeInfo(int handle, String hexPayload, String type) {
        String hex = hexPayload == null ? "" : hexPayload.trim().toLowerCase();
        String writeType = type == null || type.isBlank() ? SEND_TYPE_WRITE_REQUEST : type.trim();
        return "{\"handle\":" + handle + ",\"type\":\"" + escape(writeType) + "\",\"raw\":\"" + escape(hex) + "\"}";
    }

    public static String connSendData(String gatewayMac, String deviceMac, int handle, String hexPayload) {
        String addr = MacAddresses.compact(deviceMac);
        // Vendor: one characteristic UUID maps to one write handle (not declaration+value).
        String infos = writeInfo(handle, hexPayload);
        return command(
                gatewayMac,
                nextMsgId(),
                "conn_send_data",
                "\"devices_num\":1,\"devices\":[{\"addr\":\"" + escape(addr) + "\"}]"
                        + ",\"send_num\":1"
                        + ",\"send_infos\":[" + infos + "]"
        );
    }

    public static String connSendBuzzerPulses(String gatewayMac, String deviceMac, int handle, int pulseCount) {
        int count = Math.max(1, Math.min(15, pulseCount));
        return connSendWrites(gatewayMac, deviceMac, buzzerPulseInfos(handle, count), count * 2);
    }

    public static String command(String gatewayMac, int msgId, String cmd, String extraFields) {
        String addr = MacAddresses.compact(gatewayMac);
        int id = Math.max(1, Math.min(65_534, msgId));
        String extras = extraFields == null || extraFields.isBlank() ? "" : "," + extraFields;
        return "{\"pkt_type\":\"command\",\"gw_addr\":\"" + escape(addr) + "\",\"data\":{"
                + "\"msgId\":" + id
                + ",\"cmd\":\"" + escape(cmd) + "\""
                + extras
                + "}}";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
