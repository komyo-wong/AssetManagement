package com.assetmanagement.geotag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeotagCoordsTest {

    @Test
    void beijingGcjConvertsToKnownWgs84() {
        // coordtransform: WGS-84 39.915, 116.404 → GCJ-02 39.91640428150164, 116.41024449916938
        GeotagCoords.Point wgs = GeotagCoords.toWgs84(39.91640428150164, 116.41024449916938);
        assertEquals(39.915, wgs.lat(), 0.0003);
        assertEquals(116.404, wgs.lng(), 0.0003);
    }

    @Test
    void shenzhenParkMovesHundredsOfMeters() {
        GeotagCoords.Point wgs = GeotagCoords.toWgs84(22.640447, 114.038020);
        double meters = haversineMeters(22.640447, 114.038020, wgs.lat(), wgs.lng());
        assertTrue(meters > 200 && meters < 800, "offset was " + meters);
        assertTrue(Math.abs(wgs.lat() - 22.640447) > 0.001 || Math.abs(wgs.lng() - 114.038020) > 0.001);
    }

    @Test
    void foreignAndHkStayUnchanged() {
        assertFalse(GeotagCoords.inMainlandChina(1.3521, 103.8198));
        assertEquals(1.3521, GeotagCoords.toWgs84(1.3521, 103.8198).lat());
        assertEquals(103.8198, GeotagCoords.toWgs84(1.3521, 103.8198).lng());
        assertEquals(35.6762, GeotagCoords.toWgs84(35.6762, 139.6503).lat());
        assertFalse(GeotagCoords.inMainlandChina(22.3193, 114.1694));
        assertEquals(22.3193, GeotagCoords.toWgs84(22.3193, 114.1694).lat());
        assertEquals(114.1694, GeotagCoords.toWgs84(22.3193, 114.1694).lng());
    }

    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
