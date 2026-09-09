package com.assetmanagement.ops;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

enum PlatformCleanupTarget {
    MQTT_INBOX("mqttInbox", "MQTT 失败报文", 3, "mqtt_inbox", "received_at < ?", null),
    SCAN_EVENTS("scanEvents", "扫描流水", 2, "scan_events", "received_at < ?", null),
    PRESENCE_EVENTS("presenceEvents", "在线状态流水", 14, "beacon_presence_events", "created_at < ?", null),
    OUTBOUND_COMMANDS(
            "outboundCommands",
            "下行命令（不含排队中）",
            7,
            "mqtt_outbound_commands",
            "created_at < ? AND status <> 'PENDING'",
            null
    ),
    GATT_OPS(
            "gattOps",
            "GATT 任务（不含进行中）",
            7,
            "hcbg_gatt_ops",
            "created_at < ? AND status <> 'PENDING'",
            null
    ),
    EINK_OPS(
            "einkOps",
            "墨水屏任务（不含进行中）",
            7,
            "hcbg_eink_ops",
            "created_at < ? AND status NOT IN ('PENDING', 'RUNNING')",
            null
    ),
    EINK_JOBS(
            "einkJobs",
            "墨水屏改屏记录（不含排队）",
            7,
            "eink_push_jobs",
            "created_at < ? AND status <> 'QUEUED'",
            null
    ),
    CLOSED_ALERTS(
            "closedAlerts",
            "已关闭告警",
            30,
            "alert_events",
            "resolved_at IS NOT NULL AND resolved_at < ? AND status = 'RESOLVED'",
            null
    ),
    NOTIFICATION_DELIVERIES(
            "notificationDeliveries",
            "通知投递记录",
            30,
            "notification_deliveries",
            "created_at < ? AND status <> 'PENDING'",
            null
    ),
    AUDIT_LOGS("auditLogs", "过期审计日志", 90, "audit_logs", "created_at < ?", null);

    private final String id;
    private final String label;
    private final int defaultDays;
    private final String table;
    private final String whereSql;

    PlatformCleanupTarget(String id, String label, int defaultDays, String table, String whereSql, String ignored) {
        this.id = id;
        this.label = label;
        this.defaultDays = defaultDays;
        this.table = table;
        this.whereSql = whereSql;
    }

    String id() {
        return id;
    }

    String label() {
        return label;
    }

    int defaultDays() {
        return defaultDays;
    }

    String table() {
        return table;
    }

    String whereSql() {
        return whereSql;
    }

    static PlatformCleanupTarget fromId(String raw) {
        if (raw == null) {
            return null;
        }
        String wanted = raw.trim();
        for (PlatformCleanupTarget target : values()) {
            if (target.id.equals(wanted) || target.name().equalsIgnoreCase(wanted)) {
                return target;
            }
        }
        return null;
    }

    static List<PlatformCleanupTarget> all() {
        return List.of(values());
    }

    Map<String, Object> describe() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("label", label);
        map.put("defaultDays", defaultDays);
        map.put("table", table);
        return map;
    }
}
