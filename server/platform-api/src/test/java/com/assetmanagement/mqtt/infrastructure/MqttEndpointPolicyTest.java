package com.assetmanagement.mqtt.infrastructure;

import com.assetmanagement.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MqttEndpointPolicyTest {

    private MqttConnectionTestProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MqttConnectionTestProperties();
        properties.setTimeout(Duration.ofSeconds(2));
        properties.setAllowedPorts(Set.of(1883, 8883));
    }

    @Test
    void resolvesOnceAndPinsAnAllowedPublicAddress() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        MqttEndpointPolicy policy = new MqttEndpointPolicy(properties, ignored ->
                new InetAddress[]{publicAddress});

        var result = policy.validateAndResolve("mqtts://broker.example:8883", true);

        assertThat(result.pinnedAddress().getAddress().getHostAddress()).isEqualTo("8.8.8.8");
        assertThat(result.originalHost()).isEqualTo("broker.example");
        assertThat(result.tls()).isTrue();
    }

    @Test
    void rejectsAnyDnsAnswerThatTargetsAnInternalAddress() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        MqttEndpointPolicy policy = new MqttEndpointPolicy(properties, ignored ->
                new InetAddress[]{publicAddress, loopback});

        assertThatThrownBy(() -> policy.validateAndResolve("mqtt://broker.example:1883", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("blocked network address");
    }

    @Test
    void rejectsCredentialsQueriesAndPortsOutsideTheAllowlist() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        MqttEndpointPolicy policy = new MqttEndpointPolicy(properties, ignored ->
                new InetAddress[]{publicAddress});

        assertThatThrownBy(() -> policy.validateAndResolve(
                "mqtt://user:password@broker.example:1883", false))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.validateAndResolve(
                "mqtt://broker.example:1883?target=localhost", false))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.validateAndResolve(
                "mqtt://broker.example:22", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("allowlist");
    }

    @Test
    void permitsOnlyAnExplicitlyAllowlistedPrivateAddress() throws Exception {
        InetAddress privateAddress = InetAddress.getByAddress(new byte[]{10, 1, 2, 3});
        MqttEndpointPolicy policy = new MqttEndpointPolicy(properties, ignored ->
                new InetAddress[]{privateAddress});

        assertThatThrownBy(() -> policy.validateAndResolve("mqtt://broker.example:1883", false))
                .isInstanceOf(BusinessException.class);

        properties.setAllowedPrivateAddresses(Set.of("10.1.2.3"));
        assertThat(policy.validateAndResolve("mqtt://broker.example:1883", false)
                .pinnedAddress().getAddress().getHostAddress()).isEqualTo("10.1.2.3");
    }
}
