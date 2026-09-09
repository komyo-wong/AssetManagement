ALTER TABLE mqtt_connections
    ADD COLUMN owner_tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    ADD COLUMN endpoint_role VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN last_test_at TIMESTAMPTZ,
    ADD COLUMN last_test_successful BOOLEAN,
    ADD COLUMN last_test_code VARCHAR(40);

UPDATE mqtt_connections connection_row
SET owner_tenant_id = project_row.tenant_id
FROM projects project_row
WHERE connection_row.owner_project_id = project_row.id;

ALTER TABLE mqtt_connections DROP CONSTRAINT mqtt_connections_scope_ck;
ALTER TABLE mqtt_connections DROP CONSTRAINT mqtt_connections_scope_project_ck;
ALTER TABLE mqtt_connections
    ADD CONSTRAINT mqtt_connections_scope_ck CHECK (scope IN ('PLATFORM', 'TENANT', 'PROJECT')),
    ADD CONSTRAINT mqtt_connections_scope_owner_ck CHECK (
        (scope = 'PLATFORM' AND owner_tenant_id IS NULL AND owner_project_id IS NULL)
        OR (scope = 'TENANT' AND owner_tenant_id IS NOT NULL AND owner_project_id IS NULL)
        OR (scope = 'PROJECT' AND owner_tenant_id IS NOT NULL AND owner_project_id IS NOT NULL)
    ),
    ADD CONSTRAINT mqtt_connections_endpoint_role_ck CHECK (
        endpoint_role IN ('PRIMARY', 'STANDBY')
    ),
    ADD CONSTRAINT mqtt_connections_standby_ck CHECK (
        endpoint_role = 'PRIMARY'
        OR (standby_group IS NOT NULL AND LENGTH(TRIM(standby_group)) > 0 AND priority > 0)
    ),
    ADD CONSTRAINT mqtt_connections_priority_ck CHECK (priority BETWEEN 0 AND 1000),
    ADD CONSTRAINT mqtt_connections_archive_ck CHECK (archived_at IS NULL OR NOT enabled),
    ADD CONSTRAINT mqtt_connections_test_result_ck CHECK (
        (last_test_at IS NULL AND last_test_successful IS NULL AND last_test_code IS NULL)
        OR (last_test_at IS NOT NULL AND last_test_successful IS NOT NULL AND last_test_code IS NOT NULL)
    );

CREATE OR REPLACE FUNCTION validate_mqtt_connection_owner()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.scope = 'PROJECT' AND NOT EXISTS (
        SELECT 1
        FROM projects project_row
        WHERE project_row.id = NEW.owner_project_id
          AND project_row.tenant_id = NEW.owner_tenant_id
    ) THEN
        RAISE EXCEPTION 'MQTT project connection owner does not belong to its tenant';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_mqtt_connection_owner
    BEFORE INSERT OR UPDATE OF scope, owner_tenant_id, owner_project_id ON mqtt_connections
    FOR EACH ROW EXECUTE FUNCTION validate_mqtt_connection_owner();

DROP INDEX ux_mqtt_connections_scope_name_ci;
CREATE UNIQUE INDEX ux_mqtt_connections_active_scope_name_ci
    ON mqtt_connections (
        COALESCE(owner_tenant_id, '00000000-0000-0000-0000-000000000000'::UUID),
        COALESCE(owner_project_id, '00000000-0000-0000-0000-000000000000'::UUID),
        environment,
        LOWER(name)
    )
    WHERE archived_at IS NULL;

CREATE UNIQUE INDEX ux_mqtt_connections_active_group_primary
    ON mqtt_connections (
        COALESCE(owner_tenant_id, '00000000-0000-0000-0000-000000000000'::UUID),
        COALESCE(owner_project_id, '00000000-0000-0000-0000-000000000000'::UUID),
        environment,
        LOWER(standby_group)
    )
    WHERE archived_at IS NULL
      AND endpoint_role = 'PRIMARY'
      AND standby_group IS NOT NULL;

CREATE INDEX ix_mqtt_connections_owner_tenant
    ON mqtt_connections (owner_tenant_id, archived_at);

CREATE TABLE mqtt_connection_tenants (
    mqtt_connection_id UUID NOT NULL REFERENCES mqtt_connections(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    PRIMARY KEY (mqtt_connection_id, tenant_id)
);

CREATE INDEX ix_mqtt_connection_tenants_tenant
    ON mqtt_connection_tenants (tenant_id, mqtt_connection_id);

ALTER TABLE mqtt_connection_projects ADD COLUMN tenant_id UUID;
UPDATE mqtt_connection_projects grant_row
SET tenant_id = project_row.tenant_id
FROM projects project_row
WHERE grant_row.project_id = project_row.id;
ALTER TABLE mqtt_connection_projects ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE mqtt_connection_projects
    ADD CONSTRAINT fk_mqtt_connection_projects_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE mqtt_connection_projects
    ADD CONSTRAINT ux_mqtt_connection_projects_tenant_project
        UNIQUE (mqtt_connection_id, tenant_id, project_id);

CREATE OR REPLACE FUNCTION validate_mqtt_connection_tenant_grant()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM mqtt_connections connection_row
        WHERE connection_row.id = NEW.mqtt_connection_id
          AND connection_row.scope = 'PLATFORM'
          AND connection_row.archived_at IS NULL
    ) THEN
        RAISE EXCEPTION 'Only active platform MQTT connections use tenant grants';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_mqtt_connection_tenant_grant
    BEFORE INSERT OR UPDATE ON mqtt_connection_tenants
    FOR EACH ROW EXECUTE FUNCTION validate_mqtt_connection_tenant_grant();

CREATE OR REPLACE FUNCTION validate_mqtt_connection_project_grant()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    connection_scope VARCHAR(20);
    connection_tenant UUID;
    connection_project UUID;
BEGIN
    IF NEW.tenant_id IS NULL THEN
        SELECT project_row.tenant_id INTO NEW.tenant_id
        FROM projects project_row
        WHERE project_row.id = NEW.project_id;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM projects project_row
        WHERE project_row.id = NEW.project_id
          AND project_row.tenant_id = NEW.tenant_id
    ) THEN
        RAISE EXCEPTION 'MQTT project grant does not belong to its tenant';
    END IF;

    SELECT scope, owner_tenant_id, owner_project_id
      INTO connection_scope, connection_tenant, connection_project
    FROM mqtt_connections
    WHERE id = NEW.mqtt_connection_id AND archived_at IS NULL;

    IF connection_scope IS NULL
       OR (connection_scope = 'TENANT' AND connection_tenant <> NEW.tenant_id)
       OR (connection_scope = 'PROJECT' AND (
            connection_tenant <> NEW.tenant_id OR connection_project <> NEW.project_id
       )) THEN
        RAISE EXCEPTION 'MQTT connection is not eligible for this tenant/project grant';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER trg_validate_mqtt_topic_route_scope ON mqtt_topic_routes;
DROP FUNCTION validate_mqtt_topic_route_scope();
DROP TRIGGER IF EXISTS trg_validate_mqtt_connection_project_grant ON mqtt_connection_projects;
CREATE TRIGGER trg_validate_mqtt_connection_project_grant
    BEFORE INSERT OR UPDATE ON mqtt_connection_projects
    FOR EACH ROW EXECUTE FUNCTION validate_mqtt_connection_project_grant();

CREATE OR REPLACE FUNCTION validate_platform_mqtt_project_grant_tenant()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM mqtt_connections connection_row
        WHERE connection_row.id = NEW.mqtt_connection_id
          AND connection_row.scope = 'PLATFORM'
    ) AND NOT EXISTS (
        SELECT 1 FROM mqtt_connection_tenants tenant_grant
        WHERE tenant_grant.mqtt_connection_id = NEW.mqtt_connection_id
          AND tenant_grant.tenant_id = NEW.tenant_id
    ) THEN
        RAISE EXCEPTION 'Platform MQTT project grant requires an explicit tenant grant';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_validate_platform_mqtt_project_grant_tenant
    AFTER INSERT OR UPDATE ON mqtt_connection_projects
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_platform_mqtt_project_grant_tenant();

ALTER TABLE mqtt_topic_routes
    ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    ADD COLUMN archived_at TIMESTAMPTZ;

UPDATE mqtt_topic_routes route_row
SET tenant_id = project_row.tenant_id
FROM projects project_row
WHERE route_row.project_id = project_row.id;
ALTER TABLE mqtt_topic_routes ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE mqtt_topic_routes
    ADD CONSTRAINT mqtt_topic_routes_archive_ck CHECK (archived_at IS NULL OR NOT enabled);

ALTER TABLE mqtt_topic_routes DROP CONSTRAINT ux_mqtt_topic_routes;
CREATE UNIQUE INDEX ux_mqtt_topic_routes_active_pattern
    ON mqtt_topic_routes (mqtt_connection_id, tenant_id, project_id, direction, topic_pattern)
    WHERE archived_at IS NULL;
CREATE INDEX ix_mqtt_topic_routes_tenant_project
    ON mqtt_topic_routes (tenant_id, project_id, enabled)
    WHERE archived_at IS NULL;

CREATE OR REPLACE FUNCTION validate_mqtt_topic_route_scope()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM projects project_row
        JOIN mqtt_connections connection_row ON connection_row.id = NEW.mqtt_connection_id
        WHERE project_row.id = NEW.project_id
          AND project_row.tenant_id = NEW.tenant_id
          AND connection_row.archived_at IS NULL
          AND (
              (
                  connection_row.scope = 'PROJECT'
                  AND connection_row.owner_tenant_id = NEW.tenant_id
                  AND connection_row.owner_project_id = NEW.project_id
              )
              OR (
                  connection_row.scope = 'TENANT'
                  AND connection_row.owner_tenant_id = NEW.tenant_id
                  AND EXISTS (
                      SELECT 1 FROM mqtt_connection_projects project_grant
                      WHERE project_grant.mqtt_connection_id = connection_row.id
                        AND project_grant.tenant_id = NEW.tenant_id
                        AND project_grant.project_id = NEW.project_id
                  )
              )
              OR (
                  connection_row.scope = 'PLATFORM'
                  AND EXISTS (
                      SELECT 1 FROM mqtt_connection_tenants tenant_grant
                      WHERE tenant_grant.mqtt_connection_id = connection_row.id
                        AND tenant_grant.tenant_id = NEW.tenant_id
                  )
                  AND EXISTS (
                      SELECT 1 FROM mqtt_connection_projects project_grant
                      WHERE project_grant.mqtt_connection_id = connection_row.id
                        AND project_grant.tenant_id = NEW.tenant_id
                        AND project_grant.project_id = NEW.project_id
                  )
              )
          )
    ) THEN
        RAISE EXCEPTION 'MQTT connection is not authorized for this tenant and project';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_mqtt_topic_route_scope
    BEFORE INSERT OR UPDATE ON mqtt_topic_routes
    FOR EACH ROW EXECUTE FUNCTION validate_mqtt_topic_route_scope();

CREATE OR REPLACE FUNCTION protect_active_mqtt_grant()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'mqtt_connection_tenants' AND (
        EXISTS (
            SELECT 1 FROM mqtt_connection_projects project_grant
            WHERE project_grant.mqtt_connection_id = OLD.mqtt_connection_id
              AND project_grant.tenant_id = OLD.tenant_id
        )
        OR EXISTS (
            SELECT 1 FROM mqtt_topic_routes route_row
            WHERE route_row.mqtt_connection_id = OLD.mqtt_connection_id
              AND route_row.tenant_id = OLD.tenant_id
              AND route_row.archived_at IS NULL
        )
    ) THEN
        RAISE EXCEPTION 'Tenant grant is still used by MQTT project grants or active routes';
    END IF;
    IF TG_TABLE_NAME = 'mqtt_connection_projects' AND EXISTS (
        SELECT 1 FROM mqtt_topic_routes route_row
        WHERE route_row.mqtt_connection_id = OLD.mqtt_connection_id
          AND route_row.tenant_id = OLD.tenant_id
          AND route_row.project_id = OLD.project_id
          AND route_row.archived_at IS NULL
    ) THEN
        RAISE EXCEPTION 'Project grant is in use by an active MQTT route';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_protect_mqtt_tenant_grant
    BEFORE DELETE ON mqtt_connection_tenants
    FOR EACH ROW EXECUTE FUNCTION protect_active_mqtt_grant();
CREATE TRIGGER trg_protect_mqtt_project_grant
    BEFORE DELETE ON mqtt_connection_projects
    FOR EACH ROW EXECUTE FUNCTION protect_active_mqtt_grant();

ALTER TABLE mqtt_connection_tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE mqtt_connection_tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY mqtt_connection_tenants_isolation ON mqtt_connection_tenants
    USING (app_is_platform_admin() OR app_has_tenant_access(tenant_id))
    WITH CHECK (app_is_platform_admin() OR app_has_tenant_access(tenant_id));

DROP POLICY mqtt_connection_projects_isolation ON mqtt_connection_projects;
CREATE POLICY mqtt_connection_projects_isolation ON mqtt_connection_projects
    USING (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id))
    WITH CHECK (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id));

DROP POLICY mqtt_connections_isolation ON mqtt_connections;
CREATE POLICY mqtt_connections_isolation ON mqtt_connections
    USING (
        app_is_platform_admin()
        OR (scope = 'TENANT' AND app_has_tenant_access(owner_tenant_id))
        OR (scope = 'PROJECT' AND app_has_project_access(owner_tenant_id, owner_project_id))
        OR (
            scope = 'PLATFORM'
            AND EXISTS (
                SELECT 1 FROM mqtt_connection_tenants tenant_grant
                WHERE tenant_grant.mqtt_connection_id = mqtt_connections.id
                  AND app_has_tenant_access(tenant_grant.tenant_id)
            )
        )
    )
    WITH CHECK (
        app_is_platform_admin()
        OR (scope = 'TENANT' AND app_has_tenant_access(owner_tenant_id))
        OR (scope = 'PROJECT' AND app_has_project_access(owner_tenant_id, owner_project_id))
    );

DROP POLICY mqtt_topic_routes_isolation ON mqtt_topic_routes;
CREATE POLICY mqtt_topic_routes_isolation ON mqtt_topic_routes
    USING (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id))
    WITH CHECK (app_is_platform_admin() OR app_has_project_access(tenant_id, project_id));

COMMENT ON COLUMN mqtt_connections.owner_tenant_id IS
    'SaaS tenant owner for TENANT and PROJECT connections; PLATFORM connections require explicit grants.';
COMMENT ON COLUMN mqtt_connections.archived_at IS
    'Soft-delete marker. Archived MQTT connection rows and their encrypted history are retained for audit.';
COMMENT ON COLUMN mqtt_topic_routes.tenant_id IS
    'Denormalized tenant boundary paired with project_id and enforced by trigger and RLS.';
