package com.assetmanagement.geotag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeotagCryptoTest {

    @Test
    void encryptDecryptAndSignRoundTrip() {
        GeotagCrypto.RsaKeyPair enterprise = GeotagCrypto.generateKeyPair();
        GeotagCrypto.RsaKeyPair platform = GeotagCrypto.generateKeyPair();
        String plaintext = "{\"page\":1,\"pageSize\":10}";
        String data = GeotagCrypto.encryptEnvelope(plaintext, platform.publicKey());
        long timestamp = 1773043271220L;
        String sign = GeotagCrypto.signPayload(data, timestamp, enterprise.privateKey());

        assertTrue(GeotagCrypto.verifyPayload(data, timestamp, sign, enterprise.publicKey()));
        assertFalse(GeotagCrypto.verifyPayload(data, timestamp, sign, platform.publicKey()));
        assertEquals(plaintext, GeotagCrypto.decryptEnvelope(data, platform.privateKey()));
    }

    @Test
    void webhookUsesEnterprisePrivateKeyToDecrypt() {
        GeotagCrypto.RsaKeyPair enterprise = GeotagCrypto.generateKeyPair();
        GeotagCrypto.RsaKeyPair platform = GeotagCrypto.generateKeyPair();
        String plaintext = "{\"sn\":\"GeoTag-EQLRIDQK\",\"lat\":22.5,\"lng\":114.1}";
        String data = GeotagCrypto.encryptEnvelope(plaintext, enterprise.publicKey());
        String sign = GeotagCrypto.signPayload(data, 1L, platform.privateKey());

        assertTrue(GeotagCrypto.verifyPayload(data, 1L, sign, platform.publicKey()));
        assertEquals(plaintext, GeotagCrypto.decryptEnvelope(data, enterprise.privateKey()));
    }
}
