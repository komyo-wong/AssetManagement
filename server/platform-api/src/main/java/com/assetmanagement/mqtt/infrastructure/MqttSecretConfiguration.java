package com.assetmanagement.mqtt.infrastructure;

import com.assetmanagement.shared.security.SecretCipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SecretCipherProperties.class, MqttConnectionTestProperties.class})
public class MqttSecretConfiguration {

    @Bean
    SecretCipher mqttSecretCipher(SecretCipherProperties properties) {
        return new AesGcmSecretCipher(
                properties.getActiveKeyVersion(),
                properties.getActiveKeyBase64(),
                properties.getDecryptionKeysBase64()
        );
    }
}
