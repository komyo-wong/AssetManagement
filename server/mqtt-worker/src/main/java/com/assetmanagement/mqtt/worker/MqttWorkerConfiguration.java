package com.assetmanagement.mqtt.worker;

import com.assetmanagement.mqtt.port.MqttGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MqttWorkerProperties.class)
public class MqttWorkerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MqttWorkerConfiguration.class);

    @Bean
    ApplicationRunner verifyMqttAdapter(
            MqttWorkerProperties properties,
            ObjectProvider<MqttGateway> gatewayProvider
    ) {
        return arguments -> {
            MqttGateway gateway = gatewayProvider.getIfAvailable();
            if (properties.enabled() && gateway == null) {
                throw new IllegalStateException(
                        "MQTT worker is enabled but no MqttGateway adapter is installed"
                );
            }
            if (!properties.enabled()) {
                log.info("MQTT worker is disabled; no broker connections will be opened");
            }
        };
    }

    @Bean
    HealthIndicator mqttWorkerHealthIndicator(
            MqttWorkerProperties properties,
            ObjectProvider<MqttGateway> gatewayProvider
    ) {
        return () -> {
            if (!properties.enabled()) {
                return Health.up().withDetail("mode", "disabled").build();
            }
            return gatewayProvider.getIfAvailable() == null
                    ? Health.down().withDetail("reason", "mqtt-gateway-adapter-missing").build()
                    : Health.up().withDetail("mode", "enabled").build();
        };
    }
}

