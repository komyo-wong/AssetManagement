package com.assetmanagement.mqtt.domain;

import com.assetmanagement.project.domain.Project;
import com.assetmanagement.shared.domain.BaseEntity;
import com.assetmanagement.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "mqtt_topic_routes")
public class MqttTopicRoute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mqtt_connection_id", nullable = false, updatable = false)
    private MqttConnection connection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 24)
    private MqttRouteDirection direction;

    @Column(name = "topic_pattern", nullable = false, length = 500)
    private String topicPattern;

    @Column(name = "message_type", nullable = false, length = 80)
    private String messageType;

    @Column(name = "parser_key", nullable = false, length = 120)
    private String parserKey;

    @Column(name = "qos", nullable = false)
    private int qos;

    @Column(name = "retained", nullable = false)
    private boolean retained;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected MqttTopicRoute() {
    }

    public MqttTopicRoute(
            MqttConnection connection,
            Tenant tenant,
            Project project,
            String name,
            MqttRouteDirection direction,
            String topicPattern,
            String messageType,
            String parserKey,
            int qos
    ) {
        this.connection = connection;
        this.tenant = tenant;
        this.project = project;
        this.name = name;
        this.direction = direction;
        this.topicPattern = topicPattern;
        this.messageType = messageType;
        this.parserKey = parserKey;
        this.qos = qos;
    }

    public MqttConnection getConnection() {
        return connection;
    }

    public Project getProject() {
        return project;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getName() {
        return name;
    }

    public MqttRouteDirection getDirection() {
        return direction;
    }

    public String getTopicPattern() {
        return topicPattern;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getParserKey() {
        return parserKey;
    }

    public int getQos() {
        return qos;
    }

    public boolean isRetained() {
        return retained;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public void updateConfiguration(
            String name,
            MqttRouteDirection direction,
            String topicPattern,
            String messageType,
            String parserKey,
            int qos,
            boolean retained,
            boolean enabled
    ) {
        this.name = name;
        this.direction = direction;
        this.topicPattern = topicPattern;
        this.messageType = messageType;
        this.parserKey = parserKey;
        this.qos = qos;
        this.retained = retained;
        this.enabled = enabled;
    }

    public void archive(Instant now) {
        archivedAt = now;
        enabled = false;
    }
}
