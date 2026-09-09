-- Existing installs with empty rules get the same defaults as a new install.
-- Time indexes are created after startup (see PlatformCleanupIndexBootstrap)
-- so a large mqtt_inbox cannot block API boot.
UPDATE platform_ops_settings
SET auto_cleanup_enabled = TRUE,
    auto_cleanup_days = 1,
    cleanup_rules_json = '[{"id":"mqttInbox","days":3},{"id":"scanEvents","days":7},{"id":"presenceEvents","days":14},{"id":"outboundCommands","days":7},{"id":"gattOps","days":7},{"id":"einkOps","days":7},{"id":"einkJobs","days":7},{"id":"closedAlerts","days":30},{"id":"notificationDeliveries","days":30},{"id":"auditLogs","days":90}]'
WHERE btrim(cleanup_rules_json) IN ('', '[]');
