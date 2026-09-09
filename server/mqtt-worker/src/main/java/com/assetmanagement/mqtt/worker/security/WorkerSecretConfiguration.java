package com.assetmanagement.mqtt.worker.security;

import com.assetmanagement.shared.security.SecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class WorkerSecretConfiguration {

    @Bean
    SecretCipher secretCipher(
            @Value("${app.secrets.active-key-version}") String activeKeyVersion,
            @Value("${app.secrets.active-key-base64}") String activeKeyBase64
    ) {
        return new AesGcmSecretCipher(activeKeyVersion, activeKeyBase64, Map.of());
    }
}
