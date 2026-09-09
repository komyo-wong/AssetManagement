CREATE TABLE scan_daily_stats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    day_utc DATE NOT NULL,
    scan_count BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scan_daily_stats_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT ux_scan_daily_stats_project_day UNIQUE (project_id, day_utc)
);

CREATE INDEX ix_scan_daily_stats_project_day ON scan_daily_stats (project_id, day_utc DESC);

CREATE TRIGGER trg_scan_daily_stats_updated_at
    BEFORE UPDATE ON scan_daily_stats
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();
