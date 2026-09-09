-- Platform operations (cleanup / backup / restore / restart)

INSERT INTO permissions (code, name, module, description) VALUES
    ('platform-ops:read', 'Read platform operations', 'platform', 'View ops health, cleanup preview and backup list'),
    ('platform-ops:manage', 'Manage platform operations', 'platform', 'Purge operational data, backup/restore database, restart services')
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
  AND permission_row.code IN ('platform-ops:read', 'platform-ops:manage')
ON CONFLICT DO NOTHING;
