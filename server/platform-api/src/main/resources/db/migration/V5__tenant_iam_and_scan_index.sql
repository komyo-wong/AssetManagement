-- Scan event lookup index and MQTT downlink outbox.

CREATE INDEX IF NOT EXISTS ix_scan_events_project_beacon_received
    ON scan_events (project_id, beacon_id, received_at DESC);

CREATE TABLE IF NOT EXISTS mqtt_outbound_commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    connection_id UUID NOT NULL REFERENCES mqtt_connections(id) ON DELETE CASCADE,
    topic VARCHAR(500) NOT NULL,
    payload TEXT NOT NULL,
    qos INTEGER NOT NULL DEFAULT 0,
    retained BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(1000),
    sent_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT mqtt_outbound_commands_status_ck CHECK (
        status IN ('PENDING', 'SENT', 'FAILED')
    ),
    CONSTRAINT mqtt_outbound_commands_qos_ck CHECK (qos BETWEEN 0 AND 2),
    CONSTRAINT fk_mqtt_outbound_commands_project_tenant FOREIGN KEY (project_id, tenant_id)
        REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_mqtt_outbound_commands_status_created
    ON mqtt_outbound_commands (status, created_at ASC);
CREATE INDEX IF NOT EXISTS ix_mqtt_outbound_commands_project_created
    ON mqtt_outbound_commands (project_id, created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname = 'trg_mqtt_outbound_commands_updated_at'
    ) THEN
        CREATE TRIGGER trg_mqtt_outbound_commands_updated_at
            BEFORE UPDATE ON mqtt_outbound_commands
            FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();
    END IF;
END;
$$;

ALTER TABLE mqtt_outbound_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE mqtt_outbound_commands FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'mqtt_outbound_commands'
          AND policyname = 'mqtt_outbound_commands_isolation'
    ) THEN
        CREATE POLICY mqtt_outbound_commands_isolation ON mqtt_outbound_commands
            USING (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id))
            WITH CHECK (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id));
    END IF;
END;
$$;
