package com.assetmanagement.license;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.license")
public record PlatformLicenseProperties(String dir) {
    public PlatformLicenseProperties {
        if (dir == null || dir.isBlank()) {
            dir = "./.data/license";
        }
    }
}
