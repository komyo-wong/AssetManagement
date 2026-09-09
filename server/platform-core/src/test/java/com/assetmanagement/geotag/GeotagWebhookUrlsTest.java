package com.assetmanagement.geotag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeotagWebhookUrlsTest {

    @Test
    void omitsDefaultHttpPort() {
        assertEquals(
                "http://203.0.113.10/api/v1/public/geotag/webhook",
                GeotagWebhookUrls.build(false, "203.0.113.10", 80)
        );
    }

    @Test
    void includesCustomPort() {
        assertEquals(
                "http://203.0.113.10:18080/api/v1/public/geotag/webhook",
                GeotagWebhookUrls.build(false, "203.0.113.10", 18080)
        );
    }

    @Test
    void omitsDefaultHttpsPort() {
        assertEquals(
                "https://203.0.113.10/api/v1/public/geotag/webhook",
                GeotagWebhookUrls.build(true, "203.0.113.10", 443)
        );
    }
}
