-- GeoTag cloud GPS: platform settings, cached getList, webhook track points.

CREATE TABLE platform_geotag_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    api_base_url VARCHAR(400),
    business_no VARCHAR(80),
    platform_public_key TEXT,
    our_public_key TEXT,
    private_key_ciphertext BYTEA,
    secret_key_version VARCHAR(80),
    secret_encryption_algorithm VARCHAR(80),
    last_test_ok BOOLEAN NOT NULL DEFAULT FALSE,
    last_test_at TIMESTAMPTZ,
    last_test_message VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT platform_geotag_settings_secret_envelope_ck CHECK (
        private_key_ciphertext IS NULL
        OR (secret_key_version IS NOT NULL AND secret_encryption_algorithm IS NOT NULL)
    )
);

CREATE TRIGGER trg_platform_geotag_settings_updated_at
    BEFORE UPDATE ON platform_geotag_settings
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

INSERT INTO platform_geotag_settings (enabled)
VALUES (FALSE);

CREATE TABLE geotag_cloud_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sn VARCHAR(160) NOT NULL,
    mac VARCHAR(64),
    uuid_code VARCHAR(80),
    status INT,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT geotag_cloud_devices_sn_uk UNIQUE (sn)
);

CREATE TRIGGER trg_geotag_cloud_devices_updated_at
    BEFORE UPDATE ON geotag_cloud_devices
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE INDEX idx_geotag_cloud_devices_sn_lower ON geotag_cloud_devices (LOWER(sn));

CREATE TABLE geotag_track_points (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sn VARCHAR(160) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    address VARCHAR(500),
    battery BIGINT,
    battery_status VARCHAR(40),
    accuracy VARCHAR(40),
    confidence VARCHAR(40),
    reported_time TIMESTAMPTZ,
    location_time TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT geotag_track_points_sn_time_uk UNIQUE (sn, location_time)
);

CREATE TRIGGER trg_geotag_track_points_updated_at
    BEFORE UPDATE ON geotag_track_points
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE INDEX idx_geotag_track_points_sn_time ON geotag_track_points (sn, location_time DESC);

INSERT INTO permissions (code, name, module, description) VALUES
    ('platform-geotag:read', 'Read GeoTag integration', 'platform', 'View GeoTag cloud API settings'),
    ('platform-geotag:manage', 'Manage GeoTag integration', 'platform', 'Update GeoTag cloud API settings and test the connection')
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
  AND permission_row.code IN ('platform-geotag:read', 'platform-geotag:manage')
ON CONFLICT DO NOTHING;
