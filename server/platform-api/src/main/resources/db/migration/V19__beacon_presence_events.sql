CREATE TABLE beacon_presence_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    beacon_id UUID NOT NULL REFERENCES beacons(id) ON DELETE CASCADE,
    mac_address VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    gateway_id UUID REFERENCES gateways(id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT beacon_presence_events_status_ck CHECK (status IN ('ONLINE', 'OFFLINE')),
    CONSTRAINT beacon_presence_events_reason_ck CHECK (reason IN ('SCAN', 'TTL_EXPIRED', 'GATEWAY_OFFLINE')),
    CONSTRAINT fk_beacon_presence_events_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_beacon_presence_events_beacon_changed
    ON beacon_presence_events (beacon_id, changed_at DESC);

CREATE INDEX ix_beacon_presence_events_project_changed
    ON beacon_presence_events (project_id, changed_at DESC);

CREATE TRIGGER trg_beacon_presence_events_updated_at
    BEFORE UPDATE ON beacon_presence_events
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

ALTER TABLE beacon_presence_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE beacon_presence_events FORCE ROW LEVEL SECURITY;
CREATE POLICY beacon_presence_events_isolation ON beacon_presence_events
    USING (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id))
    WITH CHECK (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id));
