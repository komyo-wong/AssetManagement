package com.assetmanagement.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformJdbcEndpointTest {

    @Test
    void parsesStandardPostgresUrl() {
        PlatformJdbcEndpoint endpoint = PlatformJdbcEndpoint.parse(
                "jdbc:postgresql://postgres:5432/asset_management",
                "asset_app",
                "secret"
        );
        assertEquals("postgres", endpoint.host());
        assertEquals(5432, endpoint.port());
        assertEquals("asset_management", endpoint.database());
        assertEquals("asset_app", endpoint.username());
        assertEquals("secret", endpoint.password());
    }

    @Test
    void stripsQueryString() {
        PlatformJdbcEndpoint endpoint = PlatformJdbcEndpoint.parse(
                "jdbc:postgresql://127.0.0.1:5433/demo?sslmode=disable",
                "u",
                "p"
        );
        assertEquals("127.0.0.1", endpoint.host());
        assertEquals(5433, endpoint.port());
        assertEquals("demo", endpoint.database());
    }

    @Test
    void rejectsNonPostgres() {
        assertThrows(RuntimeException.class, () ->
                PlatformJdbcEndpoint.parse("jdbc:h2:mem:test", "sa", ""));
    }
}
