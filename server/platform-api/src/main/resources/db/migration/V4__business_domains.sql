-- Business domains: assets, devices, maps, MQTT inbox, tracking, alerts, inventory, collaboration.

INSERT INTO permissions (code, name, module, description) VALUES
    ('asset:read', 'Read assets', 'asset', 'Read project assets'),
    ('asset:manage', 'Manage assets', 'asset', 'Create and update project assets'),
    ('asset-type:read', 'Read asset types', 'asset', 'Read project asset types'),
    ('asset-type:manage', 'Manage asset types', 'asset', 'Create and update project asset types'),
    ('beacon:read', 'Read beacons', 'device', 'Read project beacons'),
    ('beacon:manage', 'Manage beacons', 'device', 'Create and update project beacons'),
    ('gateway:read', 'Read gateways', 'device', 'Read project gateways'),
    ('gateway:manage', 'Manage gateways', 'device', 'Create and update project gateways'),
    ('map:read', 'Read maps', 'device', 'Read project maps'),
    ('map:manage', 'Manage maps', 'device', 'Create and update project maps'),
    ('zone:read', 'Read zones', 'device', 'Read project zones'),
    ('zone:manage', 'Manage zones', 'device', 'Create and update project zones'),
    ('tracking:read', 'Read tracking', 'tracking', 'Read live and historical tracking'),
    ('roll-call:read', 'Read roll call', 'tracking', 'Read roll-call results'),
    ('roll-call:manage', 'Manage roll call', 'tracking', 'Run roll-call sessions'),
    ('alert:read', 'Read alerts', 'alert', 'Read alert events'),
    ('alert:manage', 'Manage alerts', 'alert', 'Acknowledge and resolve alerts'),
    ('alert-rule:read', 'Read alert rules', 'alert', 'Read alert rules'),
    ('alert-rule:manage', 'Manage alert rules', 'alert', 'Create and update alert rules'),
    ('inventory:read', 'Read inventory', 'inventory', 'Read inventory sessions'),
    ('inventory:manage', 'Manage inventory', 'inventory', 'Create and run inventory sessions'),
    ('project-task:read', 'Read project tasks', 'project', 'Read project tasks'),
    ('project-task:manage', 'Manage project tasks', 'project', 'Create and update project tasks'),
    ('project-document:read', 'Read project documents', 'project', 'Read project documents'),
    ('project-document:manage', 'Manage project documents', 'project', 'Create and update project documents'),
    ('analytics:read', 'Read analytics', 'analytics', 'Read analytics summaries'),
    ('dashboard:read', 'Read dashboard', 'dashboard', 'Read project dashboard summary')
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
ON CONFLICT DO NOTHING;

CREATE TABLE asset_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT asset_types_status_ck CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_asset_types_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_asset_types_project_code_ci ON asset_types (project_id, LOWER(code));
CREATE INDEX ix_asset_types_project_status ON asset_types (project_id, status);
CREATE TRIGGER trg_asset_types_updated_at
    BEFORE UPDATE ON asset_types
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    asset_type_id UUID REFERENCES asset_types(id) ON DELETE SET NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    location_label VARCHAR(240),
    archived_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT assets_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_assets_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_assets_project_code_ci ON assets (project_id, LOWER(code));
CREATE INDEX ix_assets_project_status ON assets (project_id, status);
CREATE TRIGGER trg_assets_updated_at
    BEFORE UPDATE ON assets
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE gateways (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    mac_address VARCHAR(32),
    client_id VARCHAR(120),
    status VARCHAR(24) NOT NULL DEFAULT 'OFFLINE',
    last_seen_at TIMESTAMPTZ,
    map_id UUID,
    coordinate_x DOUBLE PRECISION,
    coordinate_y DOUBLE PRECISION,
    archived_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT gateways_status_ck CHECK (status IN ('ONLINE', 'OFFLINE', 'ARCHIVED')),
    CONSTRAINT fk_gateways_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_gateways_project_code_ci ON gateways (project_id, LOWER(code));
CREATE INDEX ix_gateways_project_client ON gateways (project_id, client_id);
CREATE TRIGGER trg_gateways_updated_at
    BEFORE UPDATE ON gateways
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE beacons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    mac_address VARCHAR(32) NOT NULL,
    ibeacon_uuid VARCHAR(64),
    ibeacon_major INTEGER,
    ibeacon_minor INTEGER,
    rssi_at_1m INTEGER,
    last_rssi INTEGER,
    last_seen_at TIMESTAMPTZ,
    last_gateway_id UUID REFERENCES gateways(id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
    archived_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT beacons_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE', 'UNKNOWN', 'ARCHIVED')),
    CONSTRAINT fk_beacons_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_beacons_project_code_ci ON beacons (project_id, LOWER(code));
CREATE UNIQUE INDEX ux_beacons_project_mac_ci ON beacons (project_id, LOWER(mac_address));
CREATE INDEX ix_beacons_project_status ON beacons (project_id, status);
CREATE TRIGGER trg_beacons_updated_at
    BEFORE UPDATE ON beacons
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE asset_beacon_bindings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    beacon_id UUID NOT NULL REFERENCES beacons(id) ON DELETE CASCADE,
    bound_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unbound_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_asset_beacon_bindings_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_asset_beacon_bindings_active_beacon
    ON asset_beacon_bindings (beacon_id) WHERE active;
CREATE INDEX ix_asset_beacon_bindings_asset ON asset_beacon_bindings (asset_id, active);
CREATE TRIGGER trg_asset_beacon_bindings_updated_at
    BEFORE UPDATE ON asset_beacon_bindings
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE site_maps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    image_url VARCHAR(500),
    width_meters DOUBLE PRECISION,
    height_meters DOUBLE PRECISION,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT site_maps_status_ck CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_site_maps_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_site_maps_project_code_ci ON site_maps (project_id, LOWER(code));
CREATE TRIGGER trg_site_maps_updated_at
    BEFORE UPDATE ON site_maps
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

ALTER TABLE gateways
    ADD CONSTRAINT fk_gateways_map FOREIGN KEY (map_id) REFERENCES site_maps(id) ON DELETE SET NULL;

CREATE TABLE zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    map_id UUID REFERENCES site_maps(id) ON DELETE SET NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT zones_status_ck CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_zones_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_zones_project_code_ci ON zones (project_id, LOWER(code));
CREATE TRIGGER trg_zones_updated_at
    BEFORE UPDATE ON zones
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE mqtt_inbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    connection_id UUID REFERENCES mqtt_connections(id) ON DELETE SET NULL,
    topic VARCHAR(500) NOT NULL,
    qos INTEGER NOT NULL DEFAULT 0,
    retained BOOLEAN NOT NULL DEFAULT FALSE,
    payload_text TEXT,
    payload_sha256 VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    message_type VARCHAR(64),
    parse_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    parse_error VARCHAR(1000),
    parsed_json JSONB,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT mqtt_inbox_parse_status_ck CHECK (
        parse_status IN ('PENDING', 'PARSED', 'UNSUPPORTED', 'FAILED')
    ),
    CONSTRAINT fk_mqtt_inbox_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_mqtt_inbox_idempotency ON mqtt_inbox (project_id, idempotency_key);
CREATE INDEX ix_mqtt_inbox_project_received ON mqtt_inbox (project_id, received_at DESC);
CREATE TRIGGER trg_mqtt_inbox_updated_at
    BEFORE UPDATE ON mqtt_inbox
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE scan_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    inbox_id UUID REFERENCES mqtt_inbox(id) ON DELETE SET NULL,
    gateway_id UUID REFERENCES gateways(id) ON DELETE SET NULL,
    beacon_id UUID REFERENCES beacons(id) ON DELETE SET NULL,
    mac_address VARCHAR(32) NOT NULL,
    rssi INTEGER,
    device_name VARCHAR(160),
    device_time TIMESTAMPTZ,
    ibeacon_uuid VARCHAR(64),
    ibeacon_major INTEGER,
    ibeacon_minor INTEGER,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scan_events_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_scan_events_project_received ON scan_events (project_id, received_at DESC);
CREATE INDEX ix_scan_events_project_mac ON scan_events (project_id, LOWER(mac_address), received_at DESC);
CREATE TRIGGER trg_scan_events_updated_at
    BEFORE UPDATE ON scan_events
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE alert_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    rule_type VARCHAR(40) NOT NULL,
    threshold_value INTEGER,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT alert_rules_type_ck CHECK (rule_type IN ('BEACON_OFFLINE', 'RSSI_THRESHOLD')),
    CONSTRAINT fk_alert_rules_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_alert_rules_project_code_ci ON alert_rules (project_id, LOWER(code));
CREATE TRIGGER trg_alert_rules_updated_at
    BEFORE UPDATE ON alert_rules
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE alert_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    rule_id UUID REFERENCES alert_rules(id) ON DELETE SET NULL,
    severity VARCHAR(24) NOT NULL DEFAULT 'WARNING',
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000),
    resource_type VARCHAR(40),
    resource_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT alert_events_status_ck CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    CONSTRAINT alert_events_severity_ck CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT fk_alert_events_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_alert_events_project_status ON alert_events (project_id, status, opened_at DESC);
CREATE TRIGGER trg_alert_events_updated_at
    BEFORE UPDATE ON alert_events
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE inventory_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMPTZ,
    expected_count INTEGER NOT NULL DEFAULT 0,
    found_count INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_sessions_status_ck CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT fk_inventory_sessions_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_inventory_sessions_project_code_ci ON inventory_sessions (project_id, LOWER(code));
CREATE TRIGGER trg_inventory_sessions_updated_at
    BEFORE UPDATE ON inventory_sessions
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE inventory_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES inventory_sessions(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    found BOOLEAN NOT NULL DEFAULT FALSE,
    found_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_items_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT ux_inventory_items_session_asset UNIQUE (session_id, asset_id)
);

CREATE TRIGGER trg_inventory_items_updated_at
    BEFORE UPDATE ON inventory_items
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE project_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(24) NOT NULL DEFAULT 'TODO',
    assignee_user_id UUID REFERENCES iam_users(id) ON DELETE SET NULL,
    due_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT project_tasks_status_ck CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    CONSTRAINT fk_project_tasks_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_project_tasks_project_status ON project_tasks (project_id, status);
CREATE TRIGGER trg_project_tasks_updated_at
    BEFORE UPDATE ON project_tasks
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE project_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    file_name VARCHAR(260) NOT NULL,
    content_type VARCHAR(120),
    storage_path VARCHAR(500) NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_documents_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX ix_project_documents_project ON project_documents (project_id, created_at DESC);
CREATE TRIGGER trg_project_documents_updated_at
    BEFORE UPDATE ON project_documents
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE roll_call_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    window_seconds INTEGER NOT NULL DEFAULT 300,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMPTZ,
    expected_count INTEGER NOT NULL DEFAULT 0,
    present_count INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT roll_call_sessions_status_ck CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT fk_roll_call_sessions_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE TRIGGER trg_roll_call_sessions_updated_at
    BEFORE UPDATE ON roll_call_sessions
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE roll_call_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES roll_call_sessions(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    present BOOLEAN NOT NULL DEFAULT FALSE,
    last_seen_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_roll_call_items_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT ux_roll_call_items_session_asset UNIQUE (session_id, asset_id)
);

CREATE TRIGGER trg_roll_call_items_updated_at
    BEFORE UPDATE ON roll_call_items
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

-- RLS for all new project-scoped tables
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'asset_types', 'assets', 'gateways', 'beacons', 'asset_beacon_bindings',
        'site_maps', 'zones', 'mqtt_inbox', 'scan_events', 'alert_rules', 'alert_events',
        'inventory_sessions', 'inventory_items', 'project_tasks', 'project_documents',
        'roll_call_sessions', 'roll_call_items'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format(
            'CREATE POLICY %I ON %I USING (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id)) WITH CHECK (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id))',
            tbl || '_isolation',
            tbl
        );
    END LOOP;
END;
$$;
