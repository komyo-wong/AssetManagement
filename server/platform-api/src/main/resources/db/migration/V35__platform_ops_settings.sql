CREATE TABLE platform_ops_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auto_cleanup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_cleanup_days INT NOT NULL DEFAULT 30,
    last_auto_cleanup_at TIMESTAMPTZ,
    last_auto_cleanup_total BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT platform_ops_settings_days_ck CHECK (auto_cleanup_days >= 1 AND auto_cleanup_days <= 3650)
);

CREATE TRIGGER trg_platform_ops_settings_updated_at
    BEFORE UPDATE ON platform_ops_settings
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

INSERT INTO platform_ops_settings (auto_cleanup_enabled, auto_cleanup_days)
VALUES (FALSE, 30);
