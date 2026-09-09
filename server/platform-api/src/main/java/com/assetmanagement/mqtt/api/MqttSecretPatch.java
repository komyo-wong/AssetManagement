package com.assetmanagement.mqtt.api;

import jakarta.validation.constraints.Size;

import java.util.EnumSet;
import java.util.Set;

/**
 * Secret values are write-only. Null or blank values mean "leave unchanged";
 * removal requires an explicit entry in {@code clear}.
 */
public record MqttSecretPatch(
        @Size(max = 500) String username,
        @Size(max = 4_096) String password,
        @Size(max = 65_536) String caCertificate,
        @Size(max = 65_536) String clientCertificate,
        @Size(max = 65_536) String privateKey,
        Set<MqttSecretField> clear
) {
    public MqttSecretPatch {
        clear = clear == null || clear.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(clear));
    }

    @Override
    public String toString() {
        return "MqttSecretPatch[redacted]";
    }
}
