package com.assetmanagement.mqtt.worker.runtime;

import com.assetmanagement.device.application.HcbgGattFollowUpService;
import com.assetmanagement.mqtt.worker.MqttWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mqtt-worker", name = "enabled", havingValue = "true")
public class HcbgGattPoller {

    private static final Logger log = LoggerFactory.getLogger(HcbgGattPoller.class);

    private final WorkerRlsContext rlsContext;
    private final HcbgGattFollowUpService hcbgGattFollowUpService;
    private final MqttWorkerProperties properties;

    public HcbgGattPoller(
            WorkerRlsContext rlsContext,
            HcbgGattFollowUpService hcbgGattFollowUpService,
            MqttWorkerProperties properties
    ) {
        this.rlsContext = rlsContext;
        this.hcbgGattFollowUpService = hcbgGattFollowUpService;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 200)
    public void poll() {
        if (!properties.enabled()) {
            return;
        }
        try {
            rlsContext.asPlatform(hcbgGattFollowUpService::pollDueOps);
        } catch (Exception ex) {
            log.warn("HCBG buzzer poll failed", ex);
        }
    }
}
