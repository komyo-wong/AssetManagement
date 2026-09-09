-- Project-level GeoTag menu (map / devices / tracks), separate from asset:* and platform-geotag:*.

INSERT INTO permissions (code, name, module, description) VALUES
    ('geotag:read', 'Read GeoTag', 'geotag', 'View GeoTag map, devices and tracks'),
    ('geotag:manage', 'Manage GeoTag', 'geotag', 'Manage GeoTag project views')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_row.id, permission_row.id
FROM roles role_row
CROSS JOIN permissions permission_row
WHERE permission_row.code IN ('geotag:read', 'geotag:manage')
  AND (
    (role_row.scope = 'PLATFORM' AND LOWER(role_row.code) = LOWER('SUPER_ADMIN'))
    OR LOWER(role_row.code) IN (LOWER('TENANT_ADMIN'), LOWER('PROJECT_ADMIN'))
  )
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, geotag.id
FROM role_permissions rp
JOIN permissions asset ON asset.id = rp.permission_id AND asset.code = 'asset:read'
JOIN permissions geotag ON geotag.code = 'geotag:read'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, geotag.id
FROM role_permissions rp
JOIN permissions asset ON asset.id = rp.permission_id AND asset.code = 'asset:manage'
JOIN permissions geotag ON geotag.code = 'geotag:manage'
ON CONFLICT DO NOTHING;
