package com.assetmanagement.asset;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetImageUrlsTest {

    @Test
    void parseBuiltinAndPublicPath() {
        UUID tenant = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        assertEquals("builtin:laptop", AssetImageUrls.parse("builtin:laptop", tenant, project).orElseThrow());
        assertEquals("builtin:excavator", AssetImageUrls.parse("builtin:excavator", tenant, project).orElseThrow());
        assertEquals("builtin:phone", AssetImageUrls.parse("/asset-photos/phone.svg", tenant, project).orElseThrow());
        assertTrue(AssetImageUrls.parse("builtin:unknown", tenant, project).isEmpty());
        assertTrue(AssetImageUrls.parse("https://evil.example/x.png", tenant, project).isEmpty());
    }

    @Test
    void parseSameProjectDocumentUrl() {
        UUID tenant = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID project = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID doc = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String url = "/api/v1/tenants/" + tenant + "/projects/" + project + "/documents/" + doc + "/content";
        assertEquals(url, AssetImageUrls.parse(url, tenant, project).orElseThrow());
        assertTrue(AssetImageUrls.parse(url, UUID.randomUUID(), project).isEmpty());
    }

    @Test
    void blankClears() {
        assertTrue(AssetImageUrls.parse("  ", UUID.randomUUID(), UUID.randomUUID()).isEmpty());
        assertTrue(AssetImageUrls.parse(null, UUID.randomUUID(), UUID.randomUUID()).isEmpty());
    }
}
