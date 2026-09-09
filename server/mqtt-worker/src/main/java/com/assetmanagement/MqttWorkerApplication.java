package com.assetmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.assetmanagement")
@EnableJpaRepositories(basePackages = "com.assetmanagement")
public class MqttWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MqttWorkerApplication.class, args);
    }
}
