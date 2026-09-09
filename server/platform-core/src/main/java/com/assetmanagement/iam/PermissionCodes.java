package com.assetmanagement.iam;

/** Stable permission-code contracts shared by API modules and adapters. */
public final class PermissionCodes {

    public static final String PLATFORM_USER_READ = "platform-user:read";
    public static final String PLATFORM_USER_MANAGE = "platform-user:manage";
    public static final String PLATFORM_ROLE_READ = "platform-role:read";
    public static final String PLATFORM_ROLE_MANAGE = "platform-role:manage";
    public static final String PLATFORM_AUDIT_READ = "platform-audit:read";
    public static final String PLATFORM_GATEWAY_READ = "platform-gateway:read";
    public static final String PLATFORM_BROKER_READ = "platform-broker:read";
    public static final String PLATFORM_BROKER_MANAGE = "platform-broker:manage";
    public static final String PLATFORM_BROKER_TEST = "platform-broker:test";
    public static final String PLATFORM_MAIL_READ = "platform-mail:read";
    public static final String PLATFORM_MAIL_MANAGE = "platform-mail:manage";
    public static final String PLATFORM_BRANDING_READ = "platform-branding:read";
    public static final String PLATFORM_BRANDING_MANAGE = "platform-branding:manage";
    public static final String PLATFORM_OPS_READ = "platform-ops:read";
    public static final String PLATFORM_OPS_MANAGE = "platform-ops:manage";
    public static final String PLATFORM_GEOTAG_READ = "platform-geotag:read";
    public static final String PLATFORM_GEOTAG_MANAGE = "platform-geotag:manage";
    public static final String GEOTAG_READ = "geotag:read";
    public static final String GEOTAG_MANAGE = "geotag:manage";
    public static final String TENANT_BROKER_READ = "tenant-broker:read";
    public static final String TENANT_BROKER_MANAGE = "tenant-broker:manage";
    public static final String TENANT_BROKER_TEST = "tenant-broker:test";
    public static final String TENANT_READ = "tenant:read";
    public static final String TENANT_MEMBER_READ = "tenant-member:read";
    public static final String TENANT_MEMBER_INVITE = "tenant-member:invite";
    public static final String TENANT_MEMBER_ASSIGN = "tenant-member:assign";
    public static final String TENANT_MEMBER_REMOVE = "tenant-member:remove";
    public static final String TENANT_ROLE_READ = "tenant-role:read";
    public static final String TENANT_ROLE_MANAGE = "tenant-role:manage";
    public static final String TENANT_PROJECT_READ = "tenant-project:read";
    public static final String TENANT_PROJECT_CREATE = "tenant-project:create";
    public static final String TENANT_PROJECT_MANAGE = "tenant-project:manage";
    public static final String PROJECT_READ = "project:read";
    public static final String PROJECT_UPDATE = "project:update";
    public static final String PROJECT_MEMBER_READ = "project-member:read";
    public static final String PROJECT_MEMBER_INVITE = "project-member:invite";
    public static final String PROJECT_MEMBER_ASSIGN = "project-member:assign";
    public static final String PROJECT_MEMBER_REMOVE = "project-member:remove";
    public static final String PROJECT_ROLE_READ = "project-role:read";
    public static final String PROJECT_ROLE_MANAGE = "project-role:manage";
    public static final String MQTT_CONNECTION_READ = "mqtt-connection:read";
    public static final String MQTT_CONNECTION_CONFIGURE = "mqtt-connection:configure";
    public static final String MQTT_CONNECTION_TEST = "mqtt-connection:test";
    public static final String MQTT_TOPIC_ROUTE_READ = "mqtt-topic-route:read";
    public static final String MQTT_TOPIC_ROUTE_CONFIGURE = "mqtt-topic-route:configure";
    public static final String MQTT_MESSAGE_READ = "mqtt-message:read";
    public static final String DEVICE_COMMAND_PUBLISH = "device-command:publish";
    public static final String ASSET_READ = "asset:read";
    public static final String ASSET_MANAGE = "asset:manage";
    public static final String ASSET_TYPE_READ = "asset-type:read";
    public static final String ASSET_TYPE_MANAGE = "asset-type:manage";
    public static final String BEACON_READ = "beacon:read";
    public static final String BEACON_MANAGE = "beacon:manage";
    public static final String GATEWAY_READ = "gateway:read";
    public static final String GATEWAY_MANAGE = "gateway:manage";
    public static final String MAP_READ = "map:read";
    public static final String MAP_MANAGE = "map:manage";
    public static final String ZONE_READ = "zone:read";
    public static final String ZONE_MANAGE = "zone:manage";
    public static final String TRACKING_READ = "tracking:read";
    public static final String ROLL_CALL_READ = "roll-call:read";
    public static final String ROLL_CALL_MANAGE = "roll-call:manage";
    public static final String ALERT_READ = "alert:read";
    public static final String ALERT_MANAGE = "alert:manage";
    public static final String ALERT_RULE_READ = "alert-rule:read";
    public static final String ALERT_RULE_MANAGE = "alert-rule:manage";
    public static final String NOTIFICATION_READ = "notification:read";
    public static final String NOTIFICATION_MANAGE = "notification:manage";
    public static final String INVENTORY_READ = "inventory:read";
    public static final String INVENTORY_MANAGE = "inventory:manage";
    public static final String PROJECT_TASK_READ = "project-task:read";
    public static final String PROJECT_TASK_MANAGE = "project-task:manage";
    public static final String PROJECT_DOCUMENT_READ = "project-document:read";
    public static final String PROJECT_DOCUMENT_MANAGE = "project-document:manage";
    public static final String ANALYTICS_READ = "analytics:read";
    public static final String DASHBOARD_READ = "dashboard:read";

    private PermissionCodes() {
    }
}
