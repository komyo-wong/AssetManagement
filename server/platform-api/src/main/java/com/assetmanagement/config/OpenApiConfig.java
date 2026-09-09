package com.assetmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI assetManagementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Asset Management Platform API")
                .version("v1")
                .description("Project-isolated asset, location, alert and MQTT management API"));
    }
}

