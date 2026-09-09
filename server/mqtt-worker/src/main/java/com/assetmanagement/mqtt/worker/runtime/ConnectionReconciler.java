package com.assetmanagement.mqtt.worker.runtime;

import com.assetmanagement.mqtt.port.MqttGateway;
import com.assetmanagement.mqtt.worker.MqttWorkerProperties;
import com.assetmanagement.mqtt.worker.adapter.HiveMqWorkerGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.mqtt-worker", name = "enabled", havingValue = "true")
public class ConnectionReconciler {

    private static final Logger log = LoggerFactory.getLogger(ConnectionReconciler.class);

    private final MqttGateway mqttGateway;
    private final MqttWorkerProperties properties;

    public ConnectionReconciler(MqttGateway mqttGateway, MqttWorkerProperties properties) {
        this.mqttGateway = mqttGateway;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.mqtt-worker.reconcile-interval:30s}")
    public void reconcile() {
        if (!properties.enabled()) {
            return;
        }
        if (mqttGateway instanceof HiveMqWorkerGateway hiveMqWorkerGateway) {
            log.debug("Reconciling MQTT worker connections");
            hiveMqWorkerGateway.reconcileEnabledConnections();
        }
    }
}
