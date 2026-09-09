package com.assetmanagement.bootstrap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DevBootstrapProperties.class)
public class DevBootstrapConfiguration {
}
