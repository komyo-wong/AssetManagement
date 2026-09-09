package com.assetmanagement.mqtt.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties("app.mqtt.connection-test")
public class MqttConnectionTestProperties {

    private Duration timeout = Duration.ofSeconds(5);
    private int maxConcurrent = 4;
    private Set<Integer> allowedPorts = new LinkedHashSet<>(Set.of(1883, 8883, 80, 443));
    private Set<String> allowedPrivateAddresses = new LinkedHashSet<>();

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public Set<Integer> getAllowedPorts() {
        return allowedPorts;
    }

    public void setAllowedPorts(Set<Integer> allowedPorts) {
        this.allowedPorts = new LinkedHashSet<>(allowedPorts);
    }

    public Set<String> getAllowedPrivateAddresses() {
        return allowedPrivateAddresses;
    }

    public void setAllowedPrivateAddresses(Set<String> allowedPrivateAddresses) {
        this.allowedPrivateAddresses = new LinkedHashSet<>(allowedPrivateAddresses);
    }
}
