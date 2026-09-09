package com.assetmanagement.mqtt.worker.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically materializes beacon OFFLINE presence events (TTL / gateway offline).
 */
@Component
@ConditionalOnProperty(prefix = "app.mqtt-worker", name = "enabled", havingValue = "true")
public class BeaconPresenceReconciler {

    private final WorkerRlsContext rlsContext;
    private final BeaconPresenceRecorder presenceRecorder;

    public BeaconPresenceReconciler(WorkerRlsContext rlsContext, BeaconPresenceRecorder presenceRecorder) {
        this.rlsContext = rlsContext;
        this.presenceRecorder = presenceRecorder;
    }

    @Scheduled(fixedDelayString = "${app.gateway.presence-reconcile-interval:15s}")
    public void reconcile() {
        rlsContext.asPlatform(presenceRecorder::reconcileOffline);
    }
}
