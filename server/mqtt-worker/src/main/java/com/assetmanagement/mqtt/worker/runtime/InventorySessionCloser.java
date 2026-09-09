package com.assetmanagement.mqtt.worker.runtime;

import com.assetmanagement.inventory.application.InventoryDispatchService;
import com.assetmanagement.inventory.domain.InventorySession;
import com.assetmanagement.inventory.repository.InventoryItemRepository;
import com.assetmanagement.inventory.repository.InventorySessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Closes OPEN inventory sessions when countdown ends and tells gateways to resume scan.
 */
@Component
@ConditionalOnProperty(prefix = "app.mqtt-worker", name = "enabled", havingValue = "true")
public class InventorySessionCloser {

    private static final Logger log = LoggerFactory.getLogger(InventorySessionCloser.class);

    private final InventorySessionRepository inventorySessionRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryDispatchService inventoryDispatchService;
    private final WorkerRlsContext rlsContext;

    public InventorySessionCloser(
            InventorySessionRepository inventorySessionRepository,
            InventoryItemRepository inventoryItemRepository,
            InventoryDispatchService inventoryDispatchService,
            WorkerRlsContext rlsContext
    ) {
        this.inventorySessionRepository = inventorySessionRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryDispatchService = inventoryDispatchService;
        this.rlsContext = rlsContext;
    }

    @Scheduled(fixedDelayString = "${app.inventory.close-interval:5s}")
    public void closeExpired() {
        rlsContext.asPlatform(this::doClose);
    }

    @Transactional
    protected void doClose() {
        Instant now = Instant.now();
        var expired = inventorySessionRepository.findAllByStatusAndEndsAtBefore("OPEN", now);
        if (expired.isEmpty()) {
            return;
        }
        for (InventorySession session : expired) {
            int found = (int) inventoryItemRepository.countBySessionIdAndFoundTrue(session.getId());
            session.setCounts(session.getExpectedCount(), found);
            session.close(now);
            inventorySessionRepository.save(session);
            inventoryDispatchService.dispatchStop(session);
        }
        log.info("Closed {} expired inventory session(s) and dispatched stop", expired.size());
    }
}
