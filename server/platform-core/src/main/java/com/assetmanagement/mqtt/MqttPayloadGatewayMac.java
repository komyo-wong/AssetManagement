package com.assetmanagement.mqtt;

import com.assetmanagement.shared.util.MacAddresses;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MqttPayloadGatewayMac {

    private static final Pattern GW_ADDR = Pattern.compile("\"gw_addr\"\\s*:\\s*\"([0-9a-fA-F:-]+)\"");
    private static final Pattern GW_CN = Pattern.compile("网关\\s*[:：]\\s*([0-9a-fA-F:-]+)");
    private static final Pattern GW_JSON = Pattern.compile("\"gw\"\\s*:\\s*\"([0-9a-fA-F:-]+)\"");
    private static final Pattern GW_MAC = Pattern.compile("\"gatewayMac\"\\s*:\\s*\"([0-9a-fA-F:-]+)\"");

    private MqttPayloadGatewayMac() {
    }

    public static String extract(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        Matcher[] matchers = {
                GW_ADDR.matcher(payload),
                GW_MAC.matcher(payload),
                GW_JSON.matcher(payload),
                GW_CN.matcher(payload)
        };
        for (Matcher matcher : matchers) {
            if (matcher.find()) {
                String compact = MacAddresses.compact(matcher.group(1));
                if (!compact.isEmpty()) {
                    return compact;
                }
            }
        }
        return "";
    }
}
