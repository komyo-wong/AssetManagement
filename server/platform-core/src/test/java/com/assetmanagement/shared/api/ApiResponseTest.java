package com.assetmanagement.shared.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successResponseUsesStableEnvelope() {
        ApiResponse<String> response = ApiResponse.success("payload", "trace-1");

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.traceId()).isEqualTo("trace-1");
        assertThat(response.timestamp()).isNotNull();
    }
}

