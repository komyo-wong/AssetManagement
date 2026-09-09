-- Platform-global SMTP settings (single row) for outbound notification email.

CREATE TABLE platform_mail_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    host VARCHAR(255),
    port INT NOT NULL DEFAULT 587,
    username VARCHAR(255),
    password_ciphertext BYTEA,
    secret_key_version VARCHAR(80),
    secret_encryption_algorithm VARCHAR(80),
    from_address VARCHAR(320),
    from_name VARCHAR(120),
    use_ssl BOOLEAN NOT NULL DEFAULT FALSE,
    use_starttls BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT platform_mail_settings_port_ck CHECK (port > 0 AND port <= 65535),
    CONSTRAINT platform_mail_settings_secret_envelope_ck CHECK (
        password_ciphertext IS NULL
        OR (secret_key_version IS NOT NULL AND secret_encryption_algorithm IS NOT NULL)
    )
);

CREATE TRIGGER trg_platform_mail_settings_updated_at
    BEFORE UPDATE ON platform_mail_settings
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

INSERT INTO platform_mail_settings (enabled, port, use_ssl, use_starttls)
VALUES (FALSE, 587, FALSE, TRUE);

INSERT INTO permissions (code, name, module, description) VALUES
    ('platform-mail:read', 'Read platform mail settings', 'platform', 'View SMTP configuration'),
    ('platform-mail:manage', 'Manage platform mail settings', 'platform', 'Update SMTP configuration and send test mail')
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
  AND permission_row.code IN ('platform-mail:read', 'platform-mail:manage')
ON CONFLICT DO NOTHING;
