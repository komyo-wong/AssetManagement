package com.assetmanagement.ops;

import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.GatewayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class MosquittoGatewayAccountService {

    private static final Logger log = LoggerFactory.getLogger(MosquittoGatewayAccountService.class);

    private final PlatformDockerClient dockerClient;
    private final GatewayRepository gatewayRepository;

    public MosquittoGatewayAccountService(
            PlatformDockerClient dockerClient,
            GatewayRepository gatewayRepository
    ) {
        this.dockerClient = dockerClient;
        this.gatewayRepository = gatewayRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncExistingAccounts() {
        if (!dockerClient.available()) {
            return;
        }
        int written = 0;
        for (Gateway gateway : gatewayRepository.findAllByArchivedAtIsNullOrderByLastSeenAtDesc()) {
            if (upsertQuietly(gateway.getMqttUsername(), gateway.getMqttPassword())) {
                written++;
            }
        }
        if (written > 0) {
            dockerClient.reloadMosquitto();
            log.info("Registered {} gateway MQTT account(s) on the local broker", written);
        }
    }

    public void upsert(String username, String password) {
        if (!hasCredentials(username, password)) {
            return;
        }
        if (!dockerClient.available()) {
            log.warn("Local broker unavailable; gateway MQTT user {} was not registered", username);
            return;
        }
        try {
            dockerClient.upsertMosquittoUser(username.trim(), password);
            dockerClient.reloadMosquitto();
        } catch (RuntimeException ex) {
            log.warn("Failed to register gateway MQTT user {}: {}", username, ex.getMessage());
        }
    }

    private boolean upsertQuietly(String username, String password) {
        if (!hasCredentials(username, password)) {
            return false;
        }
        try {
            dockerClient.upsertMosquittoUser(username.trim(), password);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Failed to register gateway MQTT user {}: {}", username, ex.getMessage());
            return false;
        }
    }

    private static boolean hasCredentials(String username, String password) {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
