package com.assetmanagement.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobileApiAccessPolicyTest {

    @Test
    void deniesOpsMailProvisionAndDocs() {
        assertTrue(MobileApiAccessPolicy.denied("GET", "/api/v1/platform/ops"));
        assertTrue(MobileApiAccessPolicy.denied("POST", "/api/v1/platform/ops/restart"));
        assertTrue(MobileApiAccessPolicy.denied("PUT", "/api/v1/platform/mail-settings"));
        assertTrue(MobileApiAccessPolicy.denied("POST", "/api/v1/platform/mail-settings/test"));
        assertTrue(MobileApiAccessPolicy.denied("GET", "/api/v1/platform/geotag-settings"));
        assertTrue(MobileApiAccessPolicy.denied("POST", "/api/v1/platform/geotag-settings/test"));
        assertTrue(MobileApiAccessPolicy.denied(
                "GET",
                "/api/v1/tenants/11111111-1111-1111-1111-111111111111/projects/22222222-2222-2222-2222-222222222222/gateways/33333333-3333-3333-3333-333333333333/provision"
        ));
        assertTrue(MobileApiAccessPolicy.denied("GET", "/actuator/info"));
        assertTrue(MobileApiAccessPolicy.denied("GET", "/actuator/prometheus"));
        assertTrue(MobileApiAccessPolicy.denied("GET", "/v3/api-docs"));
        assertTrue(MobileApiAccessPolicy.denied("GET", "/swagger-ui/index.html"));
    }

    @Test
    void allowsFieldApis() {
        String base = "/api/v1/tenants/t/projects/p";
        assertFalse(MobileApiAccessPolicy.denied("GET", base + "/assets"));
        assertFalse(MobileApiAccessPolicy.denied("POST", base + "/assets/a/buzzer"));
        assertFalse(MobileApiAccessPolicy.denied("GET", base + "/tracking/live"));
        assertFalse(MobileApiAccessPolicy.denied("GET", base + "/gateways"));
        assertFalse(MobileApiAccessPolicy.denied("GET", "/api/v1/auth/me"));
        assertFalse(MobileApiAccessPolicy.denied("GET", "/api/v1/platform/users"));
        assertFalse(MobileApiAccessPolicy.denied("GET", "/actuator/health"));
    }
}
