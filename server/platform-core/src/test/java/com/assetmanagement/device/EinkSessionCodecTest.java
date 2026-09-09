package com.assetmanagement.device;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class EinkSessionCodecTest {

    @Test
    void sessionAndChunkMatchEsp32Layout() {
        assertEquals("0100a00f", EinkSessionCodec.hex(
                EinkSessionCodec.session(EinkSessionCodec.CMD_BEGIN, EinkSessionCodec.PLANE_BW, 4000)));
        assertEquals("02010000", EinkSessionCodec.hex(
                EinkSessionCodec.session(EinkSessionCodec.CMD_COMMIT, EinkSessionCodec.PLANE_RED, 0)));
        assertEquals("04000000", EinkSessionCodec.hex(
                EinkSessionCodec.session(EinkSessionCodec.CMD_REFRESH, 0, 0)));
        assertEquals("313233343536", EinkSessionCodec.asciiHex("123456"));
        assertEquals(0x11, EinkSessionCodec.statusByte("11"));

        byte[] plane = new byte[200];
        plane[0] = 0x7a;
        plane[123] = 0x5c;
        List<byte[]> chunks = EinkSessionCodec.planeChunks(plane, 0, 2);
        assertEquals(2, chunks.size());
        assertEquals(125, chunks.get(0).length);
        assertEquals(0, chunks.get(0)[0] & 0xff);
        assertEquals(0, chunks.get(0)[1] & 0xff);
        assertEquals(0x7a, chunks.get(0)[2] & 0xff);
        assertEquals(123, chunks.get(1)[0] & 0xff);
        assertEquals(0, chunks.get(1)[1] & 0xff);
        assertEquals(0x5c, chunks.get(1)[2] & 0xff);
        assertArrayEquals(new byte[]{0x7a}, new byte[]{chunks.get(0)[2]});
    }
}
