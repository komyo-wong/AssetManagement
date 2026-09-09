package com.assetmanagement.mqtt.application;

import com.assetmanagement.mqtt.domain.MqttRouteDirection;
import com.assetmanagement.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqttTopicValidatorTest {

    private final MqttTopicValidator validator = new MqttTopicValidator();

    @Test
    void acceptsControlledSubscriptionWildcardsWithoutInventingATopic() {
        validator.validate(
                MqttRouteDirection.UPLINK,
                "tenant/+/device/+/telemetry/#",
                "telemetry.v1",
                "json-map.v1",
                1,
                false
        );
    }

    @Test
    void rejectsInvalidWildcardsAndWildcardPublishing() {
        assertThatThrownBy(() -> validator.validate(
                MqttRouteDirection.UPLINK,
                "tenant/device#",
                "telemetry.v1",
                "json-map.v1",
                1,
                false
        )).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> validator.validate(
                MqttRouteDirection.DOWNLINK,
                "tenant/+/command",
                "command.v1",
                "command-map.v1",
                1,
                false
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot contain wildcards");
    }

    @Test
    void rejectsSecretAssignmentsAndRetainedUplinkRoutes() {
        assertThatThrownBy(() -> validator.validate(
                MqttRouteDirection.UPLINK,
                "devices/token=secret/telemetry",
                "telemetry.v1",
                "json-map.v1",
                1,
                false
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("secret");

        assertThatThrownBy(() -> validator.validate(
                MqttRouteDirection.ACKNOWLEDGEMENT,
                "devices/+/ack",
                "ack.v1",
                "ack-map.v1",
                1,
                true
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("downlink");
    }

    @Test
    void detectsPotentiallyAmbiguousSubscriptionFilters() {
        assertThat(validator.overlaps(
                MqttRouteDirection.UPLINK,
                "devices/+/telemetry/#",
                "devices/a/telemetry/temperature"
        )).isTrue();
        assertThat(validator.overlaps(
                MqttRouteDirection.UPLINK,
                "devices/a/status",
                "devices/b/status"
        )).isFalse();
    }
}
