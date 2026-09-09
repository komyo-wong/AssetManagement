package com.assetmanagement.shared.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacAddressesTest {

    @Test
    void normalizesColonAndCompact() {
        assertEquals("f01204b07796", MacAddresses.compact("f0:12:04:b0:77:96"));
        assertEquals("f01204b07796", MacAddresses.compact("F01204B07796"));
        assertEquals("f0:12:04:b0:77:96", MacAddresses.canonical("f01204b07796"));
        assertTrue(MacAddresses.equalsIgnoreFormat("f0:12:04:b0:77:96", "f01204b07796"));
    }
}
