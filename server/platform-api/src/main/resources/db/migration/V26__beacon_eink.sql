-- GeoTag E-lnk (e-ink) capability + push jobs (gateway ble_eink comes later)

ALTER TABLE beacons
    ADD COLUMN IF NOT EXISTS eink_capable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS eink_passkey VARCHAR(32),
    ADD COLUMN IF NOT EXISTS preferred_gateway_id UUID REFERENCES gateways (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS last_eink_gateway_id UUID REFERENCES gateways (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS last_eink_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS eink_push_jobs (
    id                  UUID PRIMARY KEY,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    tenant_id           UUID NOT NULL,
    project_id          UUID NOT NULL REFERENCES projects (id),
    asset_id            UUID NOT NULL REFERENCES assets (id),
    beacon_id           UUID NOT NULL REFERENCES beacons (id),
    gateway_id          UUID REFERENCES gateways (id) ON DELETE SET NULL,
    status              VARCHAR(24) NOT NULL,
    command_id          VARCHAR(64) NOT NULL,
    outbound_command_id UUID REFERENCES mqtt_outbound_commands (id) ON DELETE SET NULL,
    orient              VARCHAR(16),
    template_id         VARCHAR(64),
    title               VARCHAR(200),
    error_message       VARCHAR(1000),
    sent_at             TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_eink_push_jobs_project_created
    ON eink_push_jobs (project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_eink_push_jobs_asset_created
    ON eink_push_jobs (asset_id, created_at DESC);
