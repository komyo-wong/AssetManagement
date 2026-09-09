package com.assetmanagement.mqtt.parser;

import com.assetmanagement.shared.util.MacAddresses;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Hivecom HCBG01 JSON protocol (pkt_type + gw_addr + data).
 */
public final class HcbgJsonParser {

    public static final String PARSER_KEY = "hcbg-json-v1";
    public static final String SCAN_MESSAGE_TYPE = "scan_report";
    public static final String HEARTBEAT_MESSAGE_TYPE = "sta_gw_hb";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DEVICE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HcbgJsonParser() {
    }

    public record ParsedDevice(
            String addr,
            Integer rssi,
            Instant deviceTime,
            String name,
            String ibcnUuid,
            Integer ibcnMajor,
            Integer ibcnMinor,
            Integer ibcnRssiAt1m,
            String advRaw,
            String srpRaw,
            Integer battType,
            Integer battValue
    ) {
        public boolean hasScanFields() {
            return addr != null && !addr.isBlank();
        }

        public boolean hasBattery() {
            return battValue != null && battValue >= 0;
        }
    }

    public record ParsedMessage(
            String pktType,
            String reportType,
            String gatewayMac,
            Integer sequence,
            boolean heartbeat,
            List<ParsedDevice> devices,
            Map<String, Object> raw
    ) {
        public boolean isScanReport() {
            return SCAN_MESSAGE_TYPE.equalsIgnoreCase(pktType);
        }
    }

    public record DiscoveryEvent(
            String gatewayMac,
            String deviceMac,
            String discState,
            List<Integer> handles
    ) {
        public Integer firstHandle() {
            return handleAt(0);
        }

        public Integer handleAt(int index) {
            if (handles == null || index < 0 || index >= handles.size()) {
                return null;
            }
            return handles.get(index);
        }

        public boolean completed() {
            return discState != null && "completed".equalsIgnoreCase(discState.trim());
        }

        public boolean timedOut() {
            if (discState == null) {
                return false;
            }
            String normalized = discState.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            return "timed-out".equals(normalized) || "timeout".equals(normalized);
        }
    }

    public record DeviceDataEvent(
            String gatewayMac,
            String deviceMac,
            Integer handle,
            String rawHex
    ) {
    }

    public record ConnStateEvent(
            String gatewayMac,
            String deviceMac,
            String deviceState
    ) {
        public boolean readyForDiscovery() {
            if (deviceState == null) {
                return false;
            }
            // Protocol: connected = link up; enabling = CCCD; ready = 通信已准备就绪.
            return "ready".equalsIgnoreCase(deviceState.trim());
        }
    }

    /** {@code sta_data_sent}: server→device send cache emptied (actual BLE flush). */
    public record DataSentEvent(
            String gatewayMac,
            String target,
            String addr,
            Integer dataSent,
            Integer restSpace
    ) {
        public boolean toDevice() {
            return target == null || "device".equalsIgnoreCase(target.trim());
        }
    }

    public static Optional<DiscoveryEvent> tryParseDiscovery(String payload) {
        JsonNode root = parseStateRoot(payload).orElse(null);
        if (root == null) {
            return Optional.empty();
        }
        JsonNode data = root.path("data");
        String state = text(data, "state");
        if (state == null || !"sta_discovery_state".equalsIgnoreCase(state.trim())) {
            return Optional.empty();
        }
        String gatewayMac = MacAddresses.compact(text(root, "gw_addr"));
        String deviceMac = MacAddresses.compact(text(data, "device_addr"));
        String discState = text(data, "disc_state");
        List<Integer> handles = new ArrayList<>();
        JsonNode chars = data.path("chars");
        if (chars.isArray()) {
            for (JsonNode item : chars) {
                Integer handle = integer(item, "handle");
                if (handle != null && handle > 0) {
                    handles.add(handle);
                }
            }
        }
        return Optional.of(new DiscoveryEvent(
                gatewayMac.isEmpty() ? null : gatewayMac,
                deviceMac.isEmpty() ? null : deviceMac,
                discState,
                List.copyOf(handles)
        ));
    }

    public static Optional<DeviceDataEvent> tryParseDeviceData(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(payload.trim());
        } catch (Exception ex) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        String gatewayMac = MacAddresses.compact(text(root, "gw_addr"));
        JsonNode data = root.path("data");
        String pktType = text(root, "pkt_type");
        if ("state".equalsIgnoreCase(pktType)) {
            String state = text(data, "state");
            if ("sta_device_data".equalsIgnoreCase(state)) {
                return Optional.of(toDeviceData(
                        gatewayMac.isEmpty() ? null : gatewayMac,
                        MacAddresses.compact(text(data, "device_addr")),
                        data.path("recv_infos")
                ));
            }
        }
        if ("response".equalsIgnoreCase(pktType)) {
            String resp = text(data, "resp");
            if (resp != null && resp.toLowerCase(Locale.ROOT).contains("read")) {
                JsonNode infos = data.path("read_infos");
                if (!infos.isArray() || infos.isEmpty()) {
                    infos = data.path("recv_infos");
                }
                JsonNode first = infos.isArray() && infos.size() > 0 ? infos.get(0) : data;
                return Optional.of(toDeviceData(
                        gatewayMac.isEmpty() ? null : gatewayMac,
                        MacAddresses.compact(firstNonBlank(text(data, "device_addr"), text(first, "addr"))),
                        first
                ));
            }
        }
        return Optional.empty();
    }

    private static DeviceDataEvent toDeviceData(String gatewayMac, String deviceMac, JsonNode infos) {
        Integer handle = integer(infos, "handle");
        String raw = text(infos, "raw");
        if (raw == null) {
            raw = text(infos, "recv_raw");
        }
        return new DeviceDataEvent(
                gatewayMac,
                deviceMac == null || deviceMac.isEmpty() ? null : deviceMac,
                handle,
                raw
        );
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    public static Optional<DataSentEvent> tryParseDataSent(String payload) {
        JsonNode root = parseStateRoot(payload).orElse(null);
        if (root == null) {
            return Optional.empty();
        }
        JsonNode data = root.path("data");
        String state = text(data, "state");
        if (state == null || !"sta_data_sent".equalsIgnoreCase(state.trim())) {
            return Optional.empty();
        }
        String gatewayMac = MacAddresses.compact(text(root, "gw_addr"));
        String addr = MacAddresses.compact(firstNonBlank(text(data, "addr"), text(data, "device_addr")));
        return Optional.of(new DataSentEvent(
                gatewayMac.isEmpty() ? null : gatewayMac,
                text(data, "target"),
                addr.isEmpty() ? null : addr,
                integer(data, "data_sent"),
                integer(data, "rest_space")
        ));
    }

    public static Optional<ConnStateEvent> tryParseConnState(String payload) {
        JsonNode root = parseStateRoot(payload).orElse(null);
        if (root == null) {
            return Optional.empty();
        }
        JsonNode data = root.path("data");
        String state = text(data, "state");
        if (state == null || !"sta_device_state".equalsIgnoreCase(state.trim())) {
            return Optional.empty();
        }
        String gatewayMac = MacAddresses.compact(text(root, "gw_addr"));
        String deviceMac = MacAddresses.compact(text(data, "device_addr"));
        return Optional.of(new ConnStateEvent(
                gatewayMac.isEmpty() ? null : gatewayMac,
                deviceMac.isEmpty() ? null : deviceMac,
                text(data, "device_state")
        ));
    }

    private static Optional<JsonNode> parseStateRoot(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(payload.trim());
        } catch (Exception ex) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        if (!"state".equalsIgnoreCase(text(root, "pkt_type"))) {
            return Optional.empty();
        }
        return Optional.of(root);
    }

    public static Optional<ParsedMessage> tryParse(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        String trimmed = payload.trim();
        if (!trimmed.startsWith("{")) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(trimmed);
        } catch (Exception ex) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        String pktType = text(root, "pkt_type");
        if (pktType == null || pktType.isBlank()) {
            return Optional.empty();
        }
        String gatewayMac = MacAddresses.compact(text(root, "gw_addr"));
        JsonNode data = root.path("data");
        String reportType = text(data, "report_type");
        Integer sequence = integer(data, "pktSN");
        if (sequence == null) {
            sequence = integer(data, "pkt_sn");
        }
        boolean heartbeat = isHeartbeat(pktType, data);
        List<ParsedDevice> devices = new ArrayList<>();
        if (SCAN_MESSAGE_TYPE.equalsIgnoreCase(pktType) && data.isObject()) {
            JsonNode infos = data.path("dev_infos");
            if (infos.isArray()) {
                for (JsonNode item : infos) {
                    ParsedDevice device = toDevice(item);
                    if (device.hasScanFields()) {
                        devices.add(device);
                    }
                }
            }
            // report_type=ibcns_batt uses the same addr/rssi/time plus batt_type/batt_value.
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pktType", pktType);
        raw.put("reportType", reportType);
        raw.put("gatewayMac", gatewayMac.isEmpty() ? null : gatewayMac);
        raw.put("sequence", sequence);
        raw.put("heartbeat", heartbeat);
        raw.put("deviceCount", devices.size());
        return Optional.of(new ParsedMessage(
                pktType.toLowerCase(Locale.ROOT),
                reportType,
                gatewayMac.isEmpty() ? null : gatewayMac,
                sequence,
                heartbeat,
                List.copyOf(devices),
                raw
        ));
    }

    private static boolean isHeartbeat(String pktType, JsonNode data) {
        if (!"state".equalsIgnoreCase(pktType) || data == null || !data.isObject()) {
            return false;
        }
        String state = text(data, "state");
        if (state == null) {
            return false;
        }
        String normalized = state.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        return "sta_gw_hb".equals(normalized) || "state_gw_hb".equals(normalized);
    }

    private static ParsedDevice toDevice(JsonNode item) {
        String name = text(item, "name");
        if ("null".equalsIgnoreCase(name)) {
            name = null;
        }
        return new ParsedDevice(
                lower(text(item, "addr")),
                integer(item, "rssi"),
                parseDeviceTime(text(item, "time")),
                name,
                text(item, "ibcn_uuid"),
                integer(item, "ibcn_major"),
                integer(item, "ibcn_minor"),
                integer(item, "ibcn_rssi_at_1m"),
                text(item, "adv_raw"),
                text(item, "srp_raw"),
                integer(item, "batt_type"),
                integer(item, "batt_value")
        );
    }

    private static Instant parseDeviceTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DEVICE_TIME).toInstant(ZoneOffset.UTC);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static Integer integer(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            if (value != null && value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            return null;
        }
        return value.intValue();
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
