CREATE TABLE platform_install (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    install_id UUID NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_platform_install_updated_at
    BEFORE UPDATE ON platform_install
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE platform_license_used (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti UUID NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_platform_license_used_updated_at
    BEFORE UPDATE ON platform_license_used
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE platform_license_active (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti UUID NOT NULL,
    token TEXT NOT NULL,
    who VARCHAR(200),
    features VARCHAR(200) NOT NULL,
    until_date DATE,
    activated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_platform_license_active_updated_at
    BEFORE UPDATE ON platform_license_active
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

ALTER TABLE platform_branding_settings
    ADD COLUMN IF NOT EXISTS copyright_text VARCHAR(300);
