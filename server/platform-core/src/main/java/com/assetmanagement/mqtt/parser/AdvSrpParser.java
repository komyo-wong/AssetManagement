package com.assetmanagement.mqtt.parser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses gateway adv_srp uplink payloads.
 * Supports Chinese-label text from the gateway log/MQTT body.
 */
public final class AdvSrpParser {

    public static final String PARSER_KEY = "gateway-adv-srp-v1";
    public static final String MESSAGE_TYPE = "adv_srp";

    private static final Pattern LOG_PREFIX = Pattern.compile("^\\[[^\\]]+]\\s*");
    private static final Pattern GATEWAY = Pattern.compile("网关\\s*[:：]\\s*([0-9a-fA-F:-]+)");
    private static final Pattern SEQUENCE = Pattern.compile("报文序列号\\s*[:：]\\s*(\\d+)");
    private static final Pattern TYPE = Pattern.compile("报文类型\\s*[:：]\\s*([^,，]+)");
    private static final Pattern COUNT = Pattern.compile("设备数量\\s*[:：]\\s*(\\d+)");
    private static final Pattern TARGET = Pattern.compile("目标设备\\s*[:：]\\s*(\\{.*\\})\\s*$", Pattern.DOTALL);
    private static final Pattern PY_KV = Pattern.compile("'([^']+)'\\s*:\\s*(?:'([^']*)'|(-?\\d+))");
    private static final DateTimeFormatter DEVICE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AdvSrpParser() {
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
            String srpRaw
    ) {
    }

    public record ParsedMessage(
            String messageType,
            int deviceCount,
            String gatewayMac,
            Integer sequence,
            ParsedDevice target,
            Map<String, Object> raw
    ) {
    }

    public static Optional<ParsedMessage> tryParse(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        String trimmed = LOG_PREFIX.matcher(payload.trim()).replaceFirst("").trim();
        if (trimmed.startsWith("{")) {
            return parseJsonLike(trimmed);
        }
        return parseChineseText(trimmed);
    }

    private static Optional<ParsedMessage> parseChineseText(String text) {
        Matcher typeMatcher = TYPE.matcher(text);
        if (!typeMatcher.find()) {
            return Optional.empty();
        }
        String type = typeMatcher.group(1).trim();
        if (!MESSAGE_TYPE.equalsIgnoreCase(type)) {
            return Optional.empty();
        }
        int count = 0;
        Matcher countMatcher = COUNT.matcher(text);
        if (countMatcher.find()) {
            count = Integer.parseInt(countMatcher.group(1));
        }
        String gatewayMac = null;
        Matcher gatewayMatcher = GATEWAY.matcher(text);
        if (gatewayMatcher.find()) {
            gatewayMac = lower(gatewayMatcher.group(1).replace(":", ""));
        }
        Integer sequence = null;
        Matcher sequenceMatcher = SEQUENCE.matcher(text);
        if (sequenceMatcher.find()) {
            sequence = Integer.parseInt(sequenceMatcher.group(1));
        }
        Matcher targetMatcher = TARGET.matcher(text);
        if (!targetMatcher.find()) {
            return Optional.empty();
        }
        Map<String, String> kv = extractPyDict(targetMatcher.group(1));
        ParsedDevice device = toDevice(kv);
        Map<String, Object> raw = new HashMap<>();
        raw.put("messageType", type);
        raw.put("deviceCount", count);
        raw.put("gatewayMac", gatewayMac);
        raw.put("sequence", sequence);
        raw.put("target", new HashMap<>(kv));
        return Optional.of(new ParsedMessage(MESSAGE_TYPE, count, gatewayMac, sequence, device, raw));
    }

    private static Optional<ParsedMessage> parseJsonLike(String json) {
        String normalized = json.replace("\"", "'");
        Map<String, String> top = extractPyDict(normalized);
        String type = first(top, "报文类型", "type", "messageType");
        if (type == null || !MESSAGE_TYPE.equalsIgnoreCase(type)) {
            return Optional.empty();
        }
        int count = parseInt(first(top, "设备数量", "deviceCount", "count"), 0);
        String gatewayMac = lower(first(top, "网关", "gateway", "gatewayMac"));
        if (gatewayMac != null) {
            gatewayMac = gatewayMac.replace(":", "");
        }
        Integer sequence = parseInteger(first(top, "报文序列号", "sequence", "seq"));
        Map<String, String> target = top;
        String targetBlob = first(top, "目标设备", "target");
        if (targetBlob != null && targetBlob.contains(":")) {
            target = extractPyDict(targetBlob.startsWith("{") ? targetBlob : "{" + targetBlob + "}");
        }
        ParsedDevice device = toDevice(target);
        Map<String, Object> raw = new HashMap<>();
        raw.put("messageType", MESSAGE_TYPE);
        raw.put("deviceCount", count);
        raw.put("gatewayMac", gatewayMac);
        raw.put("sequence", sequence);
        raw.put("target", new HashMap<>(target));
        return Optional.of(new ParsedMessage(MESSAGE_TYPE, count, gatewayMac, sequence, device, raw));
    }

    private static Map<String, String> extractPyDict(String blob) {
        Map<String, String> map = new HashMap<>();
        Matcher matcher = PY_KV.matcher(blob);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            map.put(key, value);
        }
        return map;
    }

    private static ParsedDevice toDevice(Map<String, String> kv) {
        return new ParsedDevice(
                lower(first(kv, "addr", "mac")),
                parseInteger(first(kv, "rssi")),
                parseDeviceTime(first(kv, "time")),
                first(kv, "name"),
                first(kv, "ibcn_uuid", "uuid"),
                parseInteger(first(kv, "ibcn_major", "major")),
                parseInteger(first(kv, "ibcn_minor", "minor")),
                parseInteger(first(kv, "ibcn_rssi_at_1m")),
                first(kv, "adv_raw"),
                first(kv, "srp_raw")
        );
    }

    private static String first(Map<String, String> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        Integer parsed = parseInteger(value);
        return parsed == null ? fallback : parsed;
    }

    private static Instant parseDeviceTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DEVICE_TIME).toInstant(ZoneOffset.ofHours(8));
        } catch (Exception ex) {
            return null;
        }
    }
}
