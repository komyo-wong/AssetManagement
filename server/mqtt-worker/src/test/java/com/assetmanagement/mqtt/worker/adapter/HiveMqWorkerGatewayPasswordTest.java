package com.assetmanagement.mqtt.worker.adapter;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HiveMqWorkerGatewayPasswordTest {

    @Test
    void encodePasswordKeepsExactUtf8Bytes() {
        char[] password = "GwMqtt2026AssetMgmt".toCharArray();
        byte[] encoded = HiveMqWorkerGateway.encodePassword(password);
        assertEquals(19, encoded.length);
        assertArrayEquals("GwMqtt2026AssetMgmt".getBytes(StandardCharsets.UTF_8), encoded);
    }
}
