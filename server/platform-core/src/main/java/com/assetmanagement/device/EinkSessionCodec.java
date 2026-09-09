package com.assetmanagement.device;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** GeoTag FFE0 session frames used by ESP32 {@code ble_eink} and HCBG GATT writes. */
public final class EinkSessionCodec {

    public static final int CMD_BEGIN = 0x01;
    public static final int CMD_COMMIT = 0x02;
    public static final int CMD_ABORT = 0x03;
    public static final int CMD_REFRESH = 0x04;
    public static final int PLANE_BW = 0;
    public static final int PLANE_RED = 1;
    public static final int ST_BEGIN_OK = 0x11;
    public static final int ST_COMMIT_OK = 0x12;
    public static final int ST_REFRESH_OK = 0x13;
    public static final int ST_ERR = 0xFF;
    public static final int PLANE_LEN = 4000;
    public static final int CHUNK = 123;

    private EinkSessionCodec() {
    }

    public static byte[] session(int cmd, int plane, int len) {
        return new byte[]{
                (byte) cmd,
                (byte) plane,
                (byte) (len & 0xff),
                (byte) ((len >> 8) & 0xff)
        };
    }

    public static byte[] dataChunk(int offset, byte[] plane) {
        int remaining = plane.length - offset;
        int n = Math.min(CHUNK, remaining);
        byte[] pkt = new byte[2 + n];
        pkt[0] = (byte) (offset & 0xff);
        pkt[1] = (byte) ((offset >> 8) & 0xff);
        System.arraycopy(plane, offset, pkt, 2, n);
        return pkt;
    }

    public static String hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return sb.toString();
    }

    public static String asciiHex(String text) {
        if (text == null) {
            return "";
        }
        return hex(text.getBytes(StandardCharsets.US_ASCII));
    }

    public static Integer statusByte(String hexRaw) {
        byte[] raw = parseHex(hexRaw);
        return raw.length == 0 ? null : (raw[0] & 0xff);
    }

    public static byte[] parseHex(String hexRaw) {
        if (hexRaw == null || hexRaw.isBlank()) {
            return new byte[0];
        }
        String compact = hexRaw.trim().replace(" ", "").toLowerCase(Locale.ROOT);
        if ((compact.length() & 1) == 1) {
            return new byte[0];
        }
        byte[] out = new byte[compact.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(compact.charAt(i * 2), 16);
            int lo = Character.digit(compact.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return new byte[0];
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static List<byte[]> planeChunks(byte[] plane, int offset, int maxChunks) {
        List<byte[]> chunks = new ArrayList<>();
        int cursor = Math.max(0, offset);
        int limit = Math.max(1, maxChunks);
        while (cursor < plane.length && chunks.size() < limit) {
            byte[] chunk = dataChunk(cursor, plane);
            chunks.add(chunk);
            cursor += chunk.length - 2;
        }
        return chunks;
    }
}
