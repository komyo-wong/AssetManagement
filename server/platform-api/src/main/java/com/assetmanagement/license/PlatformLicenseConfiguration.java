package com.assetmanagement.license;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlatformLicenseProperties.class)
public class PlatformLicenseConfiguration {
}
