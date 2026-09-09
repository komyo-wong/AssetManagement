package com.assetmanagement.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ops")
public record PlatformOpsProperties(
        String backupDir,
        int maxBackups,
        String workerHealthUrl,
        String mqttHost,
        int mqttPort,
        boolean dockerEnabled,
        String dockerSocket,
        String dockerApiVersion
) {
    public PlatformOpsProperties {
        if (backupDir == null || backupDir.isBlank()) {
            backupDir = "./.data/backups";
        }
        if (maxBackups < 1) {
            maxBackups = 30;
        }
        if (workerHealthUrl == null || workerHealthUrl.isBlank()) {
            workerHealthUrl = "http://worker:8081/actuator/health";
        }
        if (mqttHost == null || mqttHost.isBlank()) {
            mqttHost = "mosquitto";
        }
        if (mqttPort <= 0) {
            mqttPort = 1883;
        }
        if (dockerSocket == null || dockerSocket.isBlank()) {
            dockerSocket = "/var/run/docker.sock";
        }
        if (dockerApiVersion == null || dockerApiVersion.isBlank()) {
            dockerApiVersion = "v1.41";
        }
    }
}
