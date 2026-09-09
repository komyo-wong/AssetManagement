-- Gateway MQTT provision fields and platform gateway overview permission.

ALTER TABLE gateways ADD COLUMN IF NOT EXISTS mqtt_username VARCHAR(120);
ALTER TABLE gateways ADD COLUMN IF NOT EXISTS uplink_topic VARCHAR(200) DEFAULT 'GwData';
ALTER TABLE gateways ADD COLUMN IF NOT EXISTS downlink_topic VARCHAR(200) DEFAULT 'SrvData';
ALTER TABLE gateways ADD COLUMN IF NOT EXISTS mqtt_qos INTEGER DEFAULT 0;
ALTER TABLE gateways ADD COLUMN IF NOT EXISTS provisioned_at TIMESTAMPTZ;

INSERT INTO permissions (code, name, module, description) VALUES
    ('platform-gateway:read', 'Read platform gateways', 'platform', 'List gateways across all tenants for platform operators')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_row.id, permission_row.id
FROM roles role_row
CROSS JOIN permissions permission_row
WHERE role_row.scope = 'PLATFORM'
  AND LOWER(role_row.code) = LOWER('SUPER_ADMIN')
  AND permission_row.code = 'platform-gateway:read'
ON CONFLICT DO NOTHING;
