import type { AppRouteRecord } from '@/types/router'
import { MQTT_CONNECTION_VIEW_PERMISSIONS, PermissionCode } from '@/constants/permissions'

/**
 * 资产管理平台产品路由。
 * 菜单可发现性由 meta.permissions / meta.roles 控制（未声明则对已登录用户可见）。
 */
export const assetPlatformRoutes: AppRouteRecord[] = [
  {
    path: '/overview',
    name: 'AssetOverview',
    component: '/product/overview',
    meta: {
      title: 'menus.overview',
      icon: 'ri:dashboard-3-line',
      keepAlive: true,
      fixedTab: true,
      permissions: [
        PermissionCode.DASHBOARD_READ,
        PermissionCode.ASSET_READ,
        PermissionCode.ALERT_READ,
        PermissionCode.ANALYTICS_READ,
        PermissionCode.GATEWAY_READ
      ]
    }
  },
  {
    path: '/account',
    name: 'UserCenter',
    component: '/product/account',
    meta: {
      title: 'menus.account',
      icon: 'ri:user-3-line',
      isHide: true,
      keepAlive: false
    }
  },
  {
    path: '/assets',
    name: 'AssetCenter',
    component: '/index/index',
    meta: {
      title: 'menus.asset.title',
      icon: 'ri:archive-stack-line',
      permissions: [
        PermissionCode.ASSET_READ,
        PermissionCode.ASSET_MANAGE,
        PermissionCode.ASSET_TYPE_READ,
        PermissionCode.ASSET_TYPE_MANAGE,
        PermissionCode.INVENTORY_READ,
        PermissionCode.INVENTORY_MANAGE,
        PermissionCode.MAP_READ
      ]
    },
    children: [
      productPage('list', 'AssetList', 'menus.asset.assets', 'ri:archive-line', '/product/assets/list', [
        PermissionCode.ASSET_READ,
        PermissionCode.ASSET_MANAGE
      ]),
      productPage('types', 'AssetTypes', 'menus.asset.types', 'ri:price-tag-3-line', '/product/assets/types', [
        PermissionCode.ASSET_TYPE_READ,
        PermissionCode.ASSET_TYPE_MANAGE
      ]),
      productPage('status', 'AssetStatus', 'menus.asset.status', 'ri:pulse-line', '/product/assets/status', [
        PermissionCode.ASSET_READ,
        PermissionCode.ASSET_MANAGE
      ]),
      productPage(
        'floorplan',
        'AssetFloorplan',
        'menus.asset.floorplan',
        'ri:map-pin-line',
        '/product/assets/floorplan',
        [PermissionCode.MAP_READ, PermissionCode.MAP_MANAGE, PermissionCode.ASSET_READ]
      ),
      productPage(
        'inventory',
        'AssetInventory',
        'menus.asset.inventory',
        'ri:clipboard-line',
        '/product/assets/inventory',
        [PermissionCode.INVENTORY_READ, PermissionCode.INVENTORY_MANAGE]
      )
    ]
  },
  {
    path: '/geotag',
    name: 'GeotagManagement',
    component: '/index/index',
    meta: {
      title: 'menus.geotag.title',
      icon: 'ri:global-line',
      requiresGeotag: true,
      permissions: [PermissionCode.GEOTAG_READ, PermissionCode.GEOTAG_MANAGE]
    },
    children: [
      productPage(
        'map',
        'GeotagMap',
        'menus.geotag.map',
        'ri:map-pin-2-line',
        '/product/geotag/map',
        [PermissionCode.GEOTAG_READ, PermissionCode.GEOTAG_MANAGE]
      ),
      productPage(
        'devices',
        'GeotagDevices',
        'menus.geotag.devices',
        'ri:smartphone-line',
        '/product/geotag/devices',
        [PermissionCode.GEOTAG_READ, PermissionCode.GEOTAG_MANAGE]
      ),
      productPage(
        'track',
        'GeotagTrack',
        'menus.geotag.track',
        'ri:route-line',
        '/product/geotag/track',
        [PermissionCode.GEOTAG_READ, PermissionCode.GEOTAG_MANAGE]
      )
    ]
  },
  {
    path: '/devices',
    name: 'DeviceAndSpace',
    component: '/index/index',
    meta: {
      title: 'menus.device.title',
      icon: 'ri:router-line',
      permissions: [
        PermissionCode.BEACON_READ,
        PermissionCode.BEACON_MANAGE,
        PermissionCode.GATEWAY_READ,
        PermissionCode.GATEWAY_MANAGE,
        PermissionCode.MAP_READ,
        PermissionCode.MAP_MANAGE,
        PermissionCode.ZONE_READ,
        PermissionCode.ZONE_MANAGE,
        PermissionCode.PROJECT_READ,
        PermissionCode.PROJECT_UPDATE
      ]
    },
    children: [
      productPage(
        'beacons',
        'BeaconManagement',
        'menus.device.beacons',
        'ri:wireless-charging-line',
        '/product/devices/beacons',
        [PermissionCode.BEACON_READ, PermissionCode.BEACON_MANAGE]
      ),
      productPage(
        'gateways',
        'GatewayManagement',
        'menus.device.gateways',
        'ri:base-station-line',
        '/product/devices/gateways',
        [PermissionCode.GATEWAY_READ, PermissionCode.GATEWAY_MANAGE]
      ),
      productPage('maps', 'MapManagement', 'menus.device.maps', 'ri:map-2-line', '/product/devices/maps', [
        PermissionCode.MAP_READ,
        PermissionCode.MAP_MANAGE
      ]),
      productPage('zones', 'ZoneManagement', 'menus.device.zones', 'ri:focus-3-line', '/product/devices/zones', [
        PermissionCode.ZONE_READ,
        PermissionCode.ZONE_MANAGE
      ]),
      productPage(
        'presence-ttl',
        'DevicePresenceTtl',
        'menus.device.presenceTtl',
        'ri:timer-line',
        '/product/devices/presence-ttl',
        [PermissionCode.PROJECT_READ, PermissionCode.PROJECT_UPDATE]
      )
    ]
  },
  {
    path: '/alerts',
    name: 'AlertCenter',
    component: '/index/index',
    meta: {
      title: 'menus.alert.title',
      icon: 'ri:alarm-warning-line',
      permissions: [
        PermissionCode.ALERT_READ,
        PermissionCode.ALERT_MANAGE,
        PermissionCode.ALERT_RULE_READ,
        PermissionCode.ALERT_RULE_MANAGE,
        PermissionCode.NOTIFICATION_READ,
        PermissionCode.NOTIFICATION_MANAGE
      ]
    },
    children: [
      productPage(
        'events',
        'AlertEvents',
        'menus.alert.events',
        'ri:notification-3-line',
        '/product/alerts/events',
        [PermissionCode.ALERT_READ, PermissionCode.ALERT_MANAGE]
      ),
      productPage(
        'rules',
        'AlertRules',
        'menus.alert.rules',
        'ri:equalizer-2-line',
        '/product/alerts/rules',
        [PermissionCode.ALERT_RULE_READ, PermissionCode.ALERT_RULE_MANAGE]
      ),
      productPage(
        'notifications',
        'MyNotifications',
        'menus.alert.notifications',
        'ri:mail-send-line',
        '/product/alerts/notifications',
        [PermissionCode.NOTIFICATION_READ, PermissionCode.NOTIFICATION_MANAGE]
      )
    ]
  },
  {
    path: '/analytics',
    name: 'DataAnalytics',
    component: '/index/index',
    meta: {
      title: 'menus.analytics.title',
      icon: 'ri:bar-chart-box-line',
      permissions: [PermissionCode.ANALYTICS_READ]
    },
    children: [
      productPage(
        'assets',
        'AssetAnalytics',
        'menus.analytics.assets',
        'ri:pie-chart-line',
        '/product/analytics/assets',
        [PermissionCode.ANALYTICS_READ]
      ),
      productPage(
        'daily-summary',
        'DailySummary',
        'menus.analytics.dailySummary',
        'ri:calendar-schedule-line',
        '/product/analytics/daily-summary',
        [PermissionCode.ANALYTICS_READ]
      )
    ]
  },
  {
    path: '/mqtt',
    name: 'MqttIntegration',
    component: '/index/index',
    meta: {
      title: 'menus.mqtt.title',
      icon: 'ri:broadcast-line',
      permissions: [...MQTT_CONNECTION_VIEW_PERMISSIONS]
    },
    children: [
      {
        path: 'connections',
        name: 'MqttConnections',
        component: '/product/mqtt',
        meta: {
          title: 'menus.mqtt.connections',
          icon: 'ri:links-line',
          keepAlive: true,
          permissions: [...MQTT_CONNECTION_VIEW_PERMISSIONS]
        }
      },
      {
        path: 'topic-routes',
        name: 'MqttTopicRules',
        component: '/product/mqtt',
        meta: {
          title: 'menus.mqtt.topicRoutes',
          icon: 'ri:route-line',
          keepAlive: true,
          permissions: [
            PermissionCode.MQTT_TOPIC_ROUTE_READ,
            PermissionCode.MQTT_TOPIC_ROUTE_CONFIGURE,
            ...MQTT_CONNECTION_VIEW_PERMISSIONS
          ]
        }
      },
      {
        path: 'messages',
        name: 'MqttMessageMonitor',
        component: '/product/mqtt',
        meta: {
          title: 'menus.mqtt.messageMonitor',
          icon: 'ri:message-3-line',
          keepAlive: true,
          permissions: [PermissionCode.MQTT_MESSAGE_READ, ...MQTT_CONNECTION_VIEW_PERMISSIONS]
        }
      },
      {
        path: 'commands',
        name: 'MqttCommands',
        component: '/product/mqtt/commands',
        meta: {
          title: 'menus.mqtt.commands',
          icon: 'ri:send-plane-line',
          keepAlive: true,
          permissions: [PermissionCode.DEVICE_COMMAND_PUBLISH, ...MQTT_CONNECTION_VIEW_PERMISSIONS]
        }
      }
    ]
  },
  {
    path: '/platform',
    name: 'PlatformAdministration',
    component: '/index/index',
    meta: {
      title: 'menus.platform.title',
      icon: 'ri:settings-4-line',
      roles: ['ROOT']
    },
    children: [
      productPage('users', 'PlatformUsers', 'menus.platform.users', 'ri:user-settings-line', '/product/platform/users'),
      productPage('roles', 'PlatformRoles', 'menus.platform.roles', 'ri:admin-line', '/product/platform/roles'),
      {
        path: 'permissions',
        name: 'PlatformPermissions',
        component: '/product/platform/permissions',
        meta: {
          title: 'menus.platform.permissions',
          icon: 'ri:key-2-line',
          keepAlive: true,
          isHide: true
        }
      },
      productPage('audit', 'AuditLog', 'menus.platform.audit', 'ri:file-shield-2-line', '/product/platform/audit'),
      productPage('mail', 'PlatformMailSettings', 'menus.platform.mail', 'ri:mail-settings-line', '/product/platform/mail'),
      productPage(
        'branding',
        'PlatformBrandingSettings',
        'menus.platform.branding',
        'ri:palette-line',
        '/product/platform/branding'
      ),
      productPage(
        'ops',
        'PlatformOps',
        'menus.platform.ops',
        'ri:tools-line',
        '/product/platform/ops'
      ),
      productPage(
        'geotag',
        'PlatformGeotagSettings',
        'menus.platform.geotag',
        'ri:cloud-line',
        '/product/platform/geotag'
      )
    ]
  }
]

function productPage(
  path: string,
  name: string,
  title: string,
  icon: string,
  component: string,
  permissions?: string[]
): AppRouteRecord {
  return {
    path,
    name,
    component,
    meta: {
      title,
      icon,
      keepAlive: true,
      ...(permissions?.length ? { permissions } : {})
    }
  }
}
