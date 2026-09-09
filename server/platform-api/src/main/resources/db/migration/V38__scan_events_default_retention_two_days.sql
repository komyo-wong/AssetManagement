-- Successful MQTT inbox rows are no longer persisted. Scan history defaults to 2 days.
-- Existing custom days (e.g. 1) are left unchanged; only the previous default of 7 is shortened.
UPDATE platform_ops_settings
SET cleanup_rules_json = replace(
        cleanup_rules_json,
        '{"id":"scanEvents","days":7}',
        '{"id":"scanEvents","days":2}'
    )
WHERE cleanup_rules_json LIKE '%{"id":"scanEvents","days":7}%';

UPDATE platform_ops_settings
SET auto_cleanup_enabled = TRUE,
    auto_cleanup_days = 1,
    cleanup_rules_json = '[{"id":"mqttInbox","days":3},{"id":"scanEvents","days":2},{"id":"presenceEvents","days":14},{"id":"outboundCommands","days":7},{"id":"gattOps","days":7},{"id":"einkOps","days":7},{"id":"einkJobs","days":7},{"id":"closedAlerts","days":30},{"id":"notificationDeliveries","days":30},{"id":"auditLogs","days":90}]'
WHERE btrim(cleanup_rules_json) IN ('', '[]');
