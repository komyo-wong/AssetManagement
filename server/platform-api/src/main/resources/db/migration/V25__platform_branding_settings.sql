-- Login / branding settings (single row) + permissions

CREATE TABLE platform_branding_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    system_name VARCHAR(120),
    login_title VARCHAR(200),
    login_subtitle VARCHAR(500),
    login_welcome_title VARCHAR(200),
    login_welcome_subtitle VARCHAR(500),
    login_background_data TEXT,
    title_logo_data TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_platform_branding_settings_updated_at
    BEFORE UPDATE ON platform_branding_settings
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

INSERT INTO platform_branding_settings (system_name)
VALUES ('资产管理平台');

INSERT INTO permissions (code, name, module, description) VALUES
    ('platform-branding:read', 'Read platform branding', 'platform', 'View login page branding settings'),
    ('platform-branding:manage', 'Manage platform branding', 'platform', 'Update login page text, background and title logo')
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
  AND permission_row.code IN ('platform-branding:read', 'platform-branding:manage')
ON CONFLICT DO NOTHING;
