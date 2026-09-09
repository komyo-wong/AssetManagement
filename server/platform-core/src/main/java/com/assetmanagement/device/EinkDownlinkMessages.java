package com.assetmanagement.device;

import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Downlink JSON for ESP32 GeoTag E-lnk push ({@code ble_eink}).
 * <p>
 * Device framebuffer: 128×250, two planes (BW + Red), 4000 bytes each.
 * GATT characteristic UUIDs are not published in the public source.
 */
public final class EinkDownlinkMessages {

    public static final String SERVICE_UUID = "";
    public static final String SESSION_CHAR = "";
    public static final String DATA_CHAR = "";
    public static final String UNLOCK_CHAR = "";
    public static final int PLANE_LEN = 4000;
    public static final int DEV_W = 128;
    public static final int DEV_H = 250;

    public static final String ZA25_SERVICE_UUID = "";
    public static final String ZA25_SESSION_CHAR = "";
    public static final String ZA25_DATA_CHAR = "";
    public static final String ZA25_UNLOCK_CHAR = "";
    public static final int ZA25_FRAME_LEN = 15000;
    public static final int ZA25_W = 200;
    public static final int ZA25_H = 300;

    private EinkDownlinkMessages() {
    }

    public static String push(
            String commandId,
            String beaconMac,
            String gatewayMacRequired,
            String passkeyOrNull,
            byte[] bwPlane,
            byte[] redPlane,
            String orientOrNull
    ) {
        if (bwPlane == null || bwPlane.length != PLANE_LEN) {
            throw new IllegalArgumentException("bw plane must be exactly " + PLANE_LEN + " bytes");
        }
        if (redPlane == null || redPlane.length != PLANE_LEN) {
            throw new IllegalArgumentException("red plane must be exactly " + PLANE_LEN + " bytes");
        }
        String gw = BuzzerDownlinkMessages.normalizeMac(gatewayMacRequired);
        if (gw.length() != 12) {
            throw new IllegalArgumentException("gw_mac is required for ble_eink");
        }
        String id = commandId == null || commandId.isBlank() ? UUID.randomUUID().toString() : commandId;
        StringBuilder sb = new StringBuilder(12000);
        sb.append("{\"cmd\":\"ble_eink\"");
        sb.append(",\"id\":\"").append(escape(id)).append('"');
        sb.append(",\"mac\":\"").append(escape(BuzzerDownlinkMessages.normalizeMac(beaconMac))).append('"');
        sb.append(",\"gw_mac\":\"").append(escape(gw)).append('"');
        sb.append(",\"service\":\"").append(SERVICE_UUID).append('"');
        sb.append(",\"session\":\"").append(SESSION_CHAR).append('"');
        sb.append(",\"data\":\"").append(DATA_CHAR).append('"');
        sb.append(",\"unlock\":\"").append(UNLOCK_CHAR).append('"');
        if (passkeyOrNull != null && !passkeyOrNull.isBlank()) {
            sb.append(",\"passkey\":\"").append(escape(passkeyOrNull.trim())).append('"');
        }
        sb.append(",\"w\":").append(DEV_W);
        sb.append(",\"h\":").append(DEV_H);
        sb.append(",\"plane_len\":").append(PLANE_LEN);
        if (orientOrNull != null && !orientOrNull.isBlank()) {
            sb.append(",\"orient\":\"").append(escape(orientOrNull.trim().toLowerCase(Locale.ROOT))).append('"');
        }
        sb.append(",\"bw\":\"").append(Base64.getEncoder().encodeToString(bwPlane)).append('"');
        sb.append(",\"red\":\"").append(Base64.getEncoder().encodeToString(redPlane)).append('"');
        sb.append('}');
        return sb.toString();
    }

    /**
     * GeoTag ZA25GM2D: single 15000-byte BWRY frame. GATT UUIDs are not in the public source.
     */
    public static String pushZa25(
            String commandId,
            String beaconMac,
            String gatewayMacRequired,
            String unlockPinOrNull,
            byte[] frame,
            String orientOrNull
    ) {
        if (frame == null || frame.length != ZA25_FRAME_LEN) {
            throw new IllegalArgumentException("za25 frame must be exactly " + ZA25_FRAME_LEN + " bytes");
        }
        String gw = BuzzerDownlinkMessages.normalizeMac(gatewayMacRequired);
        if (gw.length() != 12) {
            throw new IllegalArgumentException("gw_mac is required for ble_eink");
        }
        String id = commandId == null || commandId.isBlank() ? UUID.randomUUID().toString() : commandId;
        StringBuilder sb = new StringBuilder(22000);
        sb.append("{\"cmd\":\"ble_eink\"");
        sb.append(",\"id\":\"").append(escape(id)).append('"');
        sb.append(",\"mac\":\"").append(escape(BuzzerDownlinkMessages.normalizeMac(beaconMac))).append('"');
        sb.append(",\"gw_mac\":\"").append(escape(gw)).append('"');
        sb.append(",\"profile\":\"za25\"");
        sb.append(",\"service\":\"").append(ZA25_SERVICE_UUID).append('"');
        sb.append(",\"session\":\"").append(ZA25_SESSION_CHAR).append('"');
        sb.append(",\"data\":\"").append(ZA25_DATA_CHAR).append('"');
        sb.append(",\"unlock\":\"").append(ZA25_UNLOCK_CHAR).append('"');
        if (unlockPinOrNull == null || unlockPinOrNull.isBlank()) {
            throw new IllegalArgumentException("unlock pin is required");
        }
        sb.append(",\"unlock_pin\":\"").append(escape(unlockPinOrNull.trim())).append('"');
        sb.append(",\"w\":").append(ZA25_W);
        sb.append(",\"h\":").append(ZA25_H);
        sb.append(",\"frame_len\":").append(ZA25_FRAME_LEN);
        if (orientOrNull != null && !orientOrNull.isBlank()) {
            sb.append(",\"orient\":\"").append(escape(orientOrNull.trim().toLowerCase(Locale.ROOT))).append('"');
        }
        sb.append(",\"frame\":\"").append(Base64.getEncoder().encodeToString(frame)).append('"');
        sb.append('}');
        return sb.toString();
    }

    public static byte[] decodeFrameB64(String b64, int expectLen, String label) {
        if (b64 == null || b64.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(b64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(label + " is not valid base64");
        }
        if (raw.length != expectLen) {
            throw new IllegalArgumentException(label + " must decode to " + expectLen + " bytes, got " + raw.length);
        }
        return raw;
    }

    public static byte[] decodePlaneB64(String b64, String label) {
        return decodeFrameB64(b64, PLANE_LEN, label);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
