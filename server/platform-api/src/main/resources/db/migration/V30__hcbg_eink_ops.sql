-- HCBG FFE0 e-ink session (BEGIN / FFF2 chunks / COMMIT / REFRESH).
CREATE TABLE hcbg_eink_ops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    gateway_id UUID NOT NULL REFERENCES gateways(id) ON DELETE CASCADE,
    job_id UUID REFERENCES eink_push_jobs(id) ON DELETE SET NULL,
    beacon_mac VARCHAR(12) NOT NULL,
    passkey VARCHAR(16),
    status VARCHAR(24) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    session_handle INT,
    data_handle INT,
    unlock_handle INT,
    plane SMALLINT NOT NULL DEFAULT 0,
    send_offset INT NOT NULL DEFAULT 0,
    poll_count INT NOT NULL DEFAULT 0,
    bw BYTEA NOT NULL,
    red BYTEA NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hcbg_eink_ops_status_ck CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT hcbg_eink_ops_phase_ck CHECK (phase IN (
        'CONNECTING',
        'WAIT_BEGIN_BW',
        'SEND_BW',
        'WAIT_COMMIT_BW',
        'WAIT_BEGIN_RED',
        'SEND_RED',
        'WAIT_COMMIT_RED',
        'WAIT_REFRESH',
        'DONE'
    )),
    CONSTRAINT fk_hcbg_eink_ops_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_hcbg_eink_ops_pending
    ON hcbg_eink_ops (project_id, beacon_mac, status, created_at);

CREATE INDEX ix_hcbg_eink_ops_active
    ON hcbg_eink_ops (status, updated_at);

CREATE TRIGGER trg_hcbg_eink_ops_updated_at
    BEFORE UPDATE ON hcbg_eink_ops
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

ALTER TABLE hcbg_eink_ops ENABLE ROW LEVEL SECURITY;
ALTER TABLE hcbg_eink_ops FORCE ROW LEVEL SECURITY;
CREATE POLICY hcbg_eink_ops_isolation ON hcbg_eink_ops
    USING (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id))
    WITH CHECK (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id));
