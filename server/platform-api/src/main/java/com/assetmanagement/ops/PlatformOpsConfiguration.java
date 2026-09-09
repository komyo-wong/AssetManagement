package com.assetmanagement.ops;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlatformOpsProperties.class)
public class PlatformOpsConfiguration {
}
