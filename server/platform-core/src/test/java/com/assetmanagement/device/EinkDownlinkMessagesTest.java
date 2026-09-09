package com.assetmanagement.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EinkDownlinkMessagesTest {

    @Test
    void za25PayloadUsesFf70And15000Frame() {
        byte[] frame = new byte[EinkDownlinkMessages.ZA25_FRAME_LEN];
        String json = EinkDownlinkMessages.pushZa25(
                "cmd-1", "AA:BB:CC:DD:EE:FF", "112233445566", "246810", frame, "landscape");
        assertTrue(json.contains("\"profile\":\"za25\""));
        assertTrue(json.contains("\"unlock_pin\":\"246810\""));
        assertTrue(json.contains("\"frame_len\":15000"));
        assertEquals(EinkDownlinkMessages.ZA25_FRAME_LEN,
                EinkDownlinkMessages.decodeFrameB64(
                        json.substring(json.indexOf("\"frame\":\"") + 9, json.lastIndexOf('"')),
                        EinkDownlinkMessages.ZA25_FRAME_LEN,
                        "frame").length);
    }

    @Test
    void za25RejectsMissingUnlockPin() {
        byte[] frame = new byte[EinkDownlinkMessages.ZA25_FRAME_LEN];
        assertThrows(IllegalArgumentException.class, () ->
                EinkDownlinkMessages.pushZa25(
                        "cmd-1", "AA:BB:CC:DD:EE:FF", "112233445566", null, frame, null));
    }
}
