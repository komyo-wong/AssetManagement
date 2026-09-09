package com.assetmanagement.license;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseCodecTest {

    @Test
    void signsAndVerifiesOneInstall() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        UUID install = UUID.randomUUID();
        LicenseClaims claims = new LicenseClaims(
                UUID.randomUUID(),
                install,
                "示例客户",
                Set.of(LicenseFeature.NOTIFY, LicenseFeature.LOGIN_COPYRIGHT, LicenseFeature.EINK, LicenseFeature.BUZZ),
                LocalDate.of(2027, 12, 31),
                500,
                20
        );
        String token = LicenseCodec.issue(pair.getPrivate(), claims);
        LicenseClaims read = LicenseCodec.verify(token, pair.getPublic());
        assertEquals(claims.id(), read.id());
        assertEquals(install, read.installId());
        assertTrue(read.has(LicenseFeature.NOTIFY));
        assertTrue(read.has(LicenseFeature.LOGIN_COPYRIGHT));
        assertTrue(read.has(LicenseFeature.EINK));
        assertTrue(read.has(LicenseFeature.BUZZ));
        assertEquals(500, read.maxBeacons());
        assertEquals(20, read.maxGateways());
        assertEquals(LocalDate.of(2027, 12, 31), read.until());
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        LicenseClaims claims = new LicenseClaims(
                UUID.randomUUID(), UUID.randomUUID(), "x", Set.of(LicenseFeature.NOTIFY), null);
        String token = LicenseCodec.issue(pair.getPrivate(), claims);
        String broken = token.substring(0, token.length() - 2) + "aa";
        assertThrows(IllegalArgumentException.class, () -> LicenseCodec.verify(broken, pair.getPublic()));
    }
}
