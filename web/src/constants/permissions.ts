/** Canonical permission codes returned by `/api/v1/auth/me`. */
export const PermissionCode = {
  MQTT_CONNECTION_READ: 'mqtt-connection:read',
  MQTT_CONNECTION_CONFIGURE: 'mqtt-connection:configure',
  MQTT_CONNECTION_TEST: 'mqtt-connection:test',
  MQTT_TOPIC_ROUTE_READ: 'mqtt-topic-route:read',
  MQTT_TOPIC_ROUTE_CONFIGURE: 'mqtt-topic-route:configure',
  MQTT_MESSAGE_READ: 'mqtt-message:read',
  DEVICE_COMMAND_PUBLISH: 'device-command:publish',
  TENANT_BROKER_READ: 'tenant-broker:read',
  TENANT_BROKER_MANAGE: 'tenant-broker:manage',
  PLATFORM_BROKER_READ: 'platform-broker:read',
  PLATFORM_BROKER_MANAGE: 'platform-broker:manage',

  DASHBOARD_READ: 'dashboard:read',
  ANALYTICS_READ: 'analytics:read',
  ASSET_READ: 'asset:read',
  ASSET_MANAGE: 'asset:manage',
  ASSET_TYPE_READ: 'asset-type:read',
  ASSET_TYPE_MANAGE: 'asset-type:manage',
  INVENTORY_READ: 'inventory:read',
  INVENTORY_MANAGE: 'inventory:manage',
  BEACON_READ: 'beacon:read',
  BEACON_MANAGE: 'beacon:manage',
  GATEWAY_READ: 'gateway:read',
  GATEWAY_MANAGE: 'gateway:manage',
  MAP_READ: 'map:read',
  MAP_MANAGE: 'map:manage',
  ZONE_READ: 'zone:read',
  ZONE_MANAGE: 'zone:manage',
  PROJECT_READ: 'project:read',
  PROJECT_UPDATE: 'project:update',
  ALERT_READ: 'alert:read',
  ALERT_MANAGE: 'alert:manage',
  ALERT_RULE_READ: 'alert-rule:read',
  ALERT_RULE_MANAGE: 'alert-rule:manage',
  NOTIFICATION_READ: 'notification:read',
  NOTIFICATION_MANAGE: 'notification:manage',
  GEOTAG_READ: 'geotag:read',
  GEOTAG_MANAGE: 'geotag:manage'
} as const

export type PermissionCodeValue = (typeof PermissionCode)[keyof typeof PermissionCode]

export const MQTT_CONNECTION_VIEW_PERMISSIONS = [
  PermissionCode.MQTT_CONNECTION_READ,
  PermissionCode.MQTT_CONNECTION_CONFIGURE,
  PermissionCode.TENANT_BROKER_READ,
  PermissionCode.TENANT_BROKER_MANAGE,
  PermissionCode.PLATFORM_BROKER_READ,
  PermissionCode.PLATFORM_BROKER_MANAGE
]

export const MQTT_CONNECTION_MANAGE_PERMISSIONS = [
  PermissionCode.MQTT_CONNECTION_CONFIGURE,
  PermissionCode.TENANT_BROKER_MANAGE,
  PermissionCode.PLATFORM_BROKER_MANAGE
]
