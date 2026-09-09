-- Expand alert rule types; delivery lookup for push throttle / reminders.

ALTER TABLE alert_rules DROP CONSTRAINT IF EXISTS alert_rules_type_ck;
ALTER TABLE alert_rules ADD CONSTRAINT alert_rules_type_ck CHECK (
    rule_type IN (
        'BEACON_OFFLINE',
        'RSSI_THRESHOLD',
        'GATEWAY_OFFLINE',
        'BATTERY_LOW',
        'ASSET_OFFLINE'
    )
);

CREATE INDEX IF NOT EXISTS ix_notification_deliveries_sub_alert_kind
    ON notification_deliveries (subscription_id, alert_event_id, event_kind, created_at DESC);
