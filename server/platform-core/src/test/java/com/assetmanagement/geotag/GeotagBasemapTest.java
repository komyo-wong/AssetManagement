package com.assetmanagement.geotag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeotagBasemapTest {

    @Test
    void cartoWithoutKeyFallsBackToOsm() {
        Map<String, Object> config = GeotagBasemap.config("carto", "  ");
        assertEquals(GeotagBasemap.OSM, config.get("provider"));
        assertEquals(true, config.get("fallback"));
        assertEquals(true, config.get("needsKey"));
        assertEquals(GeotagBasemap.CARTO_APPLY_URL, config.get("applyUrl"));
        assertTrue(((List<?>) config.get("tiles")).get(0).toString().contains("openstreetmap.org"));
    }

    @Test
    void maptilerWithKeyKeepsProviderAndSupports3d() {
        Map<String, Object> config = GeotagBasemap.config("maptiler", "abc123");
        assertEquals(GeotagBasemap.MAPTILER, config.get("provider"));
        assertFalse((Boolean) config.get("fallback"));
        assertEquals(true, config.get("supports3d"));
        assertTrue(String.valueOf(config.get("styleUrl")).contains("key=abc123"));
    }

    @Test
    void osmSupports3dWithoutKey() {
        Map<String, Object> config = GeotagBasemap.catalog("osm", null, null);
        assertEquals(GeotagBasemap.OSM, config.get("provider"));
        assertEquals(true, config.get("supports3d"));
        assertEquals(4, ((List<?>) config.get("options")).size());
    }
}
