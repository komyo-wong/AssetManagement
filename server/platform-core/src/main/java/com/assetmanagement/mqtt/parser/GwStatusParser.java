package com.assetmanagement.mqtt.parser;

import com.assetmanagement.shared.util.MacAddresses;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses gateway MQTT presence payloads published to GwStatus.
 * Examples:
 * <pre>
 * 网关：30eda0caa70c,报文类型:gw_status,状态:online
 * 网关：30eda0caa70c,报文类型:gw_status,状态:offline
 * 网关：30eda0caa70c,报文类型:gw_status,状态:heartbeat
 * {"type":"gw_status","gw":"30eda0caa70c","st":"online"}
 * </pre>
 */
public final class GwStatusParser {

    public static final String PARSER_KEY = "gateway-status-v1";
    public static final String MESSAGE_TYPE = "gw_status";

    private static final Pattern LOG_PREFIX = Pattern.compile("^\\[[^\\]]+]\\s*");
    private static final Pattern GATEWAY = Pattern.compile("网关\\s*[:：]\\s*([0-9a-fA-F:-]+)");
    private static final Pattern TYPE = Pattern.compile("报文类型\\s*[:：]\\s*([^,，]+)");
    private static final Pattern STATE = Pattern.compile("状态\\s*[:：]\\s*([^,，\\s}]+)");
    private static final Pattern JSON_GW = Pattern.compile("\"gw\"\\s*:\\s*\"([0-9a-fA-F:-]+)\"");
    private static final Pattern JSON_ST = Pattern.compile("\"st\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");

    private GwStatusParser() {
    }

    public record ParsedStatus(String gatewayMac, String state, boolean online) {
    }

    public static Optional<ParsedStatus> tryParse(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        String trimmed = LOG_PREFIX.matcher(payload.trim()).replaceFirst("").trim();
        if (trimmed.startsWith("{")) {
            return parseJson(trimmed);
        }
        return parseChineseText(trimmed);
    }

    private static Optional<ParsedStatus> parseChineseText(String text) {
        Matcher typeMatcher = TYPE.matcher(text);
        if (!typeMatcher.find()) {
            return Optional.empty();
        }
        if (!MESSAGE_TYPE.equalsIgnoreCase(typeMatcher.group(1).trim())) {
            return Optional.empty();
        }
        Matcher gwMatcher = GATEWAY.matcher(text);
        Matcher stMatcher = STATE.matcher(text);
        if (!gwMatcher.find() || !stMatcher.find()) {
            return Optional.empty();
        }
        return Optional.of(normalize(gwMatcher.group(1), stMatcher.group(1)));
    }

    private static Optional<ParsedStatus> parseJson(String text) {
        Matcher typeMatcher = JSON_TYPE.matcher(text);
        if (typeMatcher.find()) {
            String type = typeMatcher.group(1).trim();
            if (!MESSAGE_TYPE.equalsIgnoreCase(type) && !"status".equalsIgnoreCase(type)) {
                return Optional.empty();
            }
        }
        Matcher gwMatcher = JSON_GW.matcher(text);
        Matcher stMatcher = JSON_ST.matcher(text);
        if (!gwMatcher.find() || !stMatcher.find()) {
            return Optional.empty();
        }
        return Optional.of(normalize(gwMatcher.group(1), stMatcher.group(1)));
    }

    private static ParsedStatus normalize(String macRaw, String stateRaw) {
        String mac = MacAddresses.compact(macRaw);
        String state = stateRaw == null ? "" : stateRaw.trim().toLowerCase(Locale.ROOT);
        boolean online = !"offline".equals(state) && !"down".equals(state) && !"0".equals(state);
        return new ParsedStatus(mac, state, online);
    }
}
