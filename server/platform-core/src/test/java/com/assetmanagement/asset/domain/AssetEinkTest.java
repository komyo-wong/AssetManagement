package com.assetmanagement.asset.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetEinkTest {

    @Test
    void updateEinkStoresOverrideAndClearsProfileWhenOff() {
        Asset asset = new Asset(UUID.randomUUID(), UUID.randomUUID(), "A-1", "Tag");
        assertNull(asset.getEinkCapable());

        asset.updateEink(true, "za25");
        assertTrue(asset.getEinkCapable());
        assertEquals("za25", asset.getEinkProfile());

        asset.updateEink(false, "elnk");
        assertFalse(asset.getEinkCapable());
        assertNull(asset.getEinkProfile());
    }
}
