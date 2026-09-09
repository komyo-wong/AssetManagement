-- Pending HCBG GATT follow-ups (buzzer writes after conn_trigger_discovery).
CREATE TABLE hcbg_gatt_ops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    gateway_id UUID NOT NULL REFERENCES gateways(id) ON DELETE CASCADE,
    beacon_mac VARCHAR(12) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    pulse_count INT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hcbg_gatt_ops_kind_ck CHECK (kind IN ('BUZZ_SHORT', 'BUZZ_LONG', 'BUZZ_STOP')),
    CONSTRAINT hcbg_gatt_ops_status_ck CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT fk_hcbg_gatt_ops_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_hcbg_gatt_ops_pending
    ON hcbg_gatt_ops (project_id, beacon_mac, status, created_at);

CREATE TRIGGER trg_hcbg_gatt_ops_updated_at
    BEFORE UPDATE ON hcbg_gatt_ops
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

ALTER TABLE hcbg_gatt_ops ENABLE ROW LEVEL SECURITY;
ALTER TABLE hcbg_gatt_ops FORCE ROW LEVEL SECURITY;
CREATE POLICY hcbg_gatt_ops_isolation ON hcbg_gatt_ops
    USING (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id))
    WITH CHECK (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id));
