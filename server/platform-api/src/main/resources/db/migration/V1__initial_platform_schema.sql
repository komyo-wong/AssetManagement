CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE iam_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120),
    preferred_locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    root_account BOOLEAN NOT NULL DEFAULT FALSE,
    protected_account BOOLEAN NOT NULL DEFAULT FALSE,
    activated_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT iam_users_status_ck CHECK (
        status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'LOCKED', 'ARCHIVED')
    ),
    CONSTRAINT iam_users_root_protected_ck CHECK (
        NOT root_account OR (protected_account AND status = 'ACTIVE')
    )
);

CREATE UNIQUE INDEX ux_iam_users_username_ci ON iam_users (LOWER(username));
CREATE UNIQUE INDEX ux_iam_users_email_ci ON iam_users (LOWER(email));
CREATE UNIQUE INDEX ux_iam_users_single_root
    ON iam_users (root_account)
    WHERE root_account;

COMMENT ON COLUMN iam_users.password_hash IS
    'One-way adaptive password hash only; never plaintext or reversible encryption.';
COMMENT ON COLUMN iam_users.root_account IS
    'Exactly one bootstrap Root account may exist; creation is handled outside this migration.';

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    default_locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    created_by_user_id UUID REFERENCES iam_users(id) ON DELETE SET NULL,
    archived_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT projects_status_ck CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT projects_archive_ck CHECK (
        (status = 'ARCHIVED' AND archived_at IS NOT NULL)
        OR (status <> 'ARCHIVED' AND archived_at IS NULL)
    )
);

CREATE UNIQUE INDEX ux_projects_code_ci ON projects (LOWER(code));
CREATE INDEX ix_projects_status ON projects (status);

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(160) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    module VARCHAR(80) NOT NULL,
    description VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(120) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    protected_role BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT roles_scope_ck CHECK (scope IN ('PLATFORM', 'PROJECT')),
    CONSTRAINT roles_scope_project_ck CHECK (
        (scope = 'PLATFORM' AND project_id IS NULL)
        OR (scope = 'PROJECT' AND project_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_roles_platform_code_ci
    ON roles (LOWER(code)) WHERE project_id IS NULL;
CREATE UNIQUE INDEX ux_roles_project_code_ci
    ON roles (project_id, LOWER(code)) WHERE project_id IS NOT NULL;
CREATE INDEX ix_roles_project_id ON roles (project_id);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_platform_roles (
    user_id UUID NOT NULL REFERENCES iam_users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE project_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES iam_users(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL DEFAULT 'INVITED',
    invited_by_user_id UUID REFERENCES iam_users(id) ON DELETE SET NULL,
    invited_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    joined_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT project_members_status_ck CHECK (
        status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REMOVED')
    ),
    CONSTRAINT project_members_joined_ck CHECK (
        status <> 'ACTIVE' OR joined_at IS NOT NULL
    ),
    CONSTRAINT ux_project_members_project_user UNIQUE (project_id, user_id)
);

CREATE INDEX ix_project_members_user_status ON project_members (user_id, status);
CREATE INDEX ix_project_members_project_status ON project_members (project_id, status);

CREATE TABLE project_member_roles (
    project_member_id UUID NOT NULL REFERENCES project_members(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (project_member_id, role_id)
);

CREATE OR REPLACE FUNCTION validate_platform_role_assignment()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM roles
        WHERE id = NEW.role_id AND scope = 'PLATFORM' AND project_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Only PLATFORM roles may be assigned as user platform roles';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_user_platform_role
    BEFORE INSERT OR UPDATE ON user_platform_roles
    FOR EACH ROW EXECUTE FUNCTION validate_platform_role_assignment();

CREATE OR REPLACE FUNCTION validate_project_member_role_assignment()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM project_members member_row
        JOIN roles role_row ON role_row.id = NEW.role_id
        WHERE member_row.id = NEW.project_member_id
          AND role_row.scope = 'PROJECT'
          AND role_row.project_id = member_row.project_id
    ) THEN
        RAISE EXCEPTION 'A project member role must belong to the same project';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_project_member_role
    BEFORE INSERT OR UPDATE ON project_member_roles
    FOR EACH ROW EXECUTE FUNCTION validate_project_member_role_assignment();

CREATE TABLE mqtt_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    owner_project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    environment VARCHAR(24) NOT NULL,
    broker_uri VARCHAR(500) NOT NULL,
    protocol_version VARCHAR(24) NOT NULL,
    client_id_template VARCHAR(180) NOT NULL,
    tls_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 100,
    standby_group VARCHAR(80),
    username_ciphertext BYTEA,
    password_ciphertext BYTEA,
    ca_certificate_ciphertext BYTEA,
    client_certificate_ciphertext BYTEA,
    private_key_ciphertext BYTEA,
    secret_key_version VARCHAR(80),
    secret_encryption_algorithm VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT mqtt_connections_scope_ck CHECK (scope IN ('PLATFORM', 'PROJECT')),
    CONSTRAINT mqtt_connections_scope_project_ck CHECK (
        (scope = 'PLATFORM' AND owner_project_id IS NULL)
        OR (scope = 'PROJECT' AND owner_project_id IS NOT NULL)
    ),
    CONSTRAINT mqtt_connections_environment_ck CHECK (
        environment IN ('DEVELOPMENT', 'TEST', 'STAGING', 'PRODUCTION')
    ),
    CONSTRAINT mqtt_connections_protocol_ck CHECK (
        protocol_version IN ('MQTT_3_1_1', 'MQTT_5_0')
    ),
    CONSTRAINT mqtt_connections_uri_ck CHECK (
        broker_uri ~* '^(mqtt|mqtts|ws|wss)://'
    ),
    CONSTRAINT mqtt_connections_secret_envelope_ck CHECK (
        (
            username_ciphertext IS NULL
            AND password_ciphertext IS NULL
            AND ca_certificate_ciphertext IS NULL
            AND client_certificate_ciphertext IS NULL
            AND private_key_ciphertext IS NULL
        )
        OR (
            secret_key_version IS NOT NULL
            AND secret_encryption_algorithm IS NOT NULL
        )
    )
);

CREATE UNIQUE INDEX ux_mqtt_connections_scope_name_ci
    ON mqtt_connections (COALESCE(owner_project_id, '00000000-0000-0000-0000-000000000000'::UUID),
                         environment,
                         LOWER(name));
CREATE INDEX ix_mqtt_connections_runtime
    ON mqtt_connections (environment, enabled, priority);
CREATE INDEX ix_mqtt_connections_owner_project ON mqtt_connections (owner_project_id);

COMMENT ON COLUMN mqtt_connections.username_ciphertext IS
    'Envelope-encrypted secret; never store or return plaintext credentials.';
COMMENT ON COLUMN mqtt_connections.password_ciphertext IS
    'Envelope-encrypted secret; never store or return plaintext credentials.';
COMMENT ON COLUMN mqtt_connections.private_key_ciphertext IS
    'Envelope-encrypted private key; never store or return plaintext key material.';

CREATE TABLE mqtt_connection_projects (
    mqtt_connection_id UUID NOT NULL REFERENCES mqtt_connections(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    PRIMARY KEY (mqtt_connection_id, project_id)
);

CREATE INDEX ix_mqtt_connection_projects_project ON mqtt_connection_projects (project_id);

CREATE TABLE mqtt_topic_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mqtt_connection_id UUID NOT NULL REFERENCES mqtt_connections(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    direction VARCHAR(24) NOT NULL,
    topic_pattern VARCHAR(500) NOT NULL,
    message_type VARCHAR(80) NOT NULL,
    parser_key VARCHAR(120) NOT NULL,
    qos INTEGER NOT NULL DEFAULT 1,
    retained BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT mqtt_topic_routes_direction_ck CHECK (
        direction IN ('UPLINK', 'DOWNLINK', 'ACKNOWLEDGEMENT')
    ),
    CONSTRAINT mqtt_topic_routes_qos_ck CHECK (qos BETWEEN 0 AND 2),
    CONSTRAINT ux_mqtt_topic_routes UNIQUE (
        mqtt_connection_id,
        project_id,
        direction,
        topic_pattern
    )
);

CREATE INDEX ix_mqtt_topic_routes_project ON mqtt_topic_routes (project_id, enabled);
CREATE INDEX ix_mqtt_topic_routes_connection ON mqtt_topic_routes (mqtt_connection_id, enabled);

CREATE OR REPLACE FUNCTION validate_mqtt_topic_route_scope()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM mqtt_connections connection_row
        WHERE connection_row.id = NEW.mqtt_connection_id
          AND (
              (
                  connection_row.scope = 'PROJECT'
                  AND connection_row.owner_project_id = NEW.project_id
              )
              OR (
                  connection_row.scope = 'PLATFORM'
                  AND EXISTS (
                      SELECT 1
                      FROM mqtt_connection_projects grant_row
                      WHERE grant_row.mqtt_connection_id = connection_row.id
                        AND grant_row.project_id = NEW.project_id
                  )
              )
          )
    ) THEN
        RAISE EXCEPTION 'MQTT connection is not authorized for this project';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_mqtt_topic_route_scope
    BEFORE INSERT OR UPDATE ON mqtt_topic_routes
    FOR EACH ROW EXECUTE FUNCTION validate_mqtt_topic_route_scope();

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES iam_users(id) ON DELETE SET NULL,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    action VARCHAR(160) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(160),
    successful BOOLEAN NOT NULL,
    trace_id VARCHAR(80),
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_audit_logs_project_created ON audit_logs (project_id, created_at DESC);
CREATE INDEX ix_audit_logs_actor_created ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX ix_audit_logs_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX ix_audit_logs_details_gin ON audit_logs USING GIN (details);

CREATE OR REPLACE FUNCTION set_row_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_iam_users_updated_at
    BEFORE UPDATE ON iam_users
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();
CREATE TRIGGER trg_projects_updated_at
    BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();
CREATE TRIGGER trg_permissions_updated_at
    BEFORE UPDATE ON permissions
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();
CREATE TRIGGER trg_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();
CREATE TRIGGER trg_project_members_updated_at
    BEFORE UPDATE ON project_members
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();
CREATE TRIGGER trg_mqtt_connections_updated_at
    BEFORE UPDATE ON mqtt_connections
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();
CREATE TRIGGER trg_mqtt_topic_routes_updated_at
    BEFORE UPDATE ON mqtt_topic_routes
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE OR REPLACE FUNCTION protect_root_account()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.root_account THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'The Root account cannot be deleted';
        END IF;
        IF NOT NEW.root_account OR NOT NEW.protected_account OR NEW.status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'Root protection cannot be removed and Root must remain active';
        END IF;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        RETURN NEW;
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_protect_root_account
    BEFORE UPDATE OR DELETE ON iam_users
    FOR EACH ROW EXECUTE FUNCTION protect_root_account();

CREATE OR REPLACE FUNCTION reject_audit_log_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Audit log rows are append-only';
END;
$$;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();

-- The application sets these transaction-local values after authentication:
--   SET LOCAL app.current_project_id = '<uuid>';
--   SET LOCAL app.is_platform_admin = 'false';
-- Missing context intentionally denies access to project-scoped RLS tables.
CREATE OR REPLACE FUNCTION app_is_platform_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE(current_setting('app.is_platform_admin', TRUE), 'false') = 'true';
$$;

CREATE OR REPLACE FUNCTION app_has_project_access(row_project_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT app_is_platform_admin()
        OR row_project_id = NULLIF(current_setting('app.current_project_id', TRUE), '')::UUID;
$$;

ALTER TABLE project_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_members FORCE ROW LEVEL SECURITY;
CREATE POLICY project_members_isolation ON project_members
    USING (app_has_project_access(project_id))
    WITH CHECK (app_has_project_access(project_id));

ALTER TABLE mqtt_connection_projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE mqtt_connection_projects FORCE ROW LEVEL SECURITY;
CREATE POLICY mqtt_connection_projects_isolation ON mqtt_connection_projects
    USING (app_has_project_access(project_id))
    WITH CHECK (app_has_project_access(project_id));

ALTER TABLE mqtt_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE mqtt_connections FORCE ROW LEVEL SECURITY;
CREATE POLICY mqtt_connections_isolation ON mqtt_connections
    USING (
        app_is_platform_admin()
        OR (scope = 'PROJECT' AND app_has_project_access(owner_project_id))
        OR (
            scope = 'PLATFORM'
            AND EXISTS (
                SELECT 1
                FROM mqtt_connection_projects grant_row
                WHERE grant_row.mqtt_connection_id = mqtt_connections.id
                  AND app_has_project_access(grant_row.project_id)
            )
        )
    )
    WITH CHECK (
        app_is_platform_admin()
        OR (scope = 'PROJECT' AND app_has_project_access(owner_project_id))
    );

ALTER TABLE mqtt_topic_routes ENABLE ROW LEVEL SECURITY;
ALTER TABLE mqtt_topic_routes FORCE ROW LEVEL SECURITY;
CREATE POLICY mqtt_topic_routes_isolation ON mqtt_topic_routes
    USING (app_has_project_access(project_id))
    WITH CHECK (app_has_project_access(project_id));

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_logs_isolation ON audit_logs
    USING (app_has_project_access(project_id))
    WITH CHECK (app_has_project_access(project_id));
