package com.assetmanagement.mqtt.worker.runtime;

import com.assetmanagement.device.application.HcbgEinkFollowUpService;
import com.assetmanagement.mqtt.worker.MqttWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mqtt-worker", name = "enabled", havingValue = "true")
public class HcbgEinkPoller {

    private static final Logger log = LoggerFactory.getLogger(HcbgEinkPoller.class);

    private final WorkerRlsContext rlsContext;
    private final HcbgEinkFollowUpService hcbgEinkFollowUpService;
    private final MqttWorkerProperties properties;

    public HcbgEinkPoller(
            WorkerRlsContext rlsContext,
            HcbgEinkFollowUpService hcbgEinkFollowUpService,
            MqttWorkerProperties properties
    ) {
        this.rlsContext = rlsContext;
        this.hcbgEinkFollowUpService = hcbgEinkFollowUpService;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        if (!properties.enabled()) {
            return;
        }
        try {
            rlsContext.asPlatform(hcbgEinkFollowUpService::pollDueOps);
        } catch (Exception ex) {
            log.warn("HCBG e-ink poll failed", ex);
        }
    }
}
