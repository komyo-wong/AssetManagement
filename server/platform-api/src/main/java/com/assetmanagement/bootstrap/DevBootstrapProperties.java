package com.assetmanagement.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.dev-bootstrap")
public class DevBootstrapProperties {

    private boolean enabled;
    private String rootUsername = "root";
    private String rootEmail = "root@local.dev";
    private String rootPassword = "";
    private String rootDisplayName = "Root";
    private String tenantCode = "demo";
    private String tenantName = "Demo Tenant";
    private String projectCode = "demo-project";
    private String projectName = "Demo Project";
    private boolean mqttEnabled;
    private String mqttBrokerUri = "";
    private String mqttUsername = "";
    private String mqttPassword = "";
    private String mqttConnectionName = "本机 Broker";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRootUsername() {
        return rootUsername;
    }

    public void setRootUsername(String rootUsername) {
        this.rootUsername = rootUsername;
    }

    public String getRootEmail() {
        return rootEmail;
    }

    public void setRootEmail(String rootEmail) {
        this.rootEmail = rootEmail;
    }

    public String getRootPassword() {
        return rootPassword;
    }

    public void setRootPassword(String rootPassword) {
        this.rootPassword = rootPassword;
    }

    public String getRootDisplayName() {
        return rootDisplayName;
    }

    public void setRootDisplayName(String rootDisplayName) {
        this.rootDisplayName = rootDisplayName;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public boolean isMqttEnabled() {
        return mqttEnabled;
    }

    public void setMqttEnabled(boolean mqttEnabled) {
        this.mqttEnabled = mqttEnabled;
    }

    public String getMqttBrokerUri() {
        return mqttBrokerUri;
    }

    public void setMqttBrokerUri(String mqttBrokerUri) {
        this.mqttBrokerUri = mqttBrokerUri;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public void setMqttUsername(String mqttUsername) {
        this.mqttUsername = mqttUsername;
    }

    public String getMqttPassword() {
        return mqttPassword;
    }

    public void setMqttPassword(String mqttPassword) {
        this.mqttPassword = mqttPassword;
    }

    public String getMqttConnectionName() {
        return mqttConnectionName;
    }

    public void setMqttConnectionName(String mqttConnectionName) {
        this.mqttConnectionName = mqttConnectionName;
    }
}
