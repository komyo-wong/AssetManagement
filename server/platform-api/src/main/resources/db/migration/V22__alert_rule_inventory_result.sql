-- Allow inventory result alert rules.

ALTER TABLE alert_rules DROP CONSTRAINT IF EXISTS alert_rules_type_ck;
ALTER TABLE alert_rules ADD CONSTRAINT alert_rules_type_ck CHECK (
    rule_type IN (
        'BEACON_OFFLINE',
        'RSSI_THRESHOLD',
        'GATEWAY_OFFLINE',
        'BATTERY_LOW',
        'ASSET_OFFLINE',
        'INVENTORY_RESULT'
    )
);
