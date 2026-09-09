-- SaaS tenancy and authentication state. This migration intentionally creates
-- no user and no credential; Root bootstrap remains an offline, audited task.

ALTER TABLE iam_users
    ADD COLUMN authorization_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE tenants (
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
    CONSTRAINT tenants_status_ck CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT tenants_archive_ck CHECK (
        (status = 'ARCHIVED' AND archived_at IS NOT NULL)
        OR (status <> 'ARCHIVED' AND archived_at IS NULL)
    )
);

CREATE UNIQUE INDEX ux_tenants_code_ci ON tenants (LOWER(code));
CREATE INDEX ix_tenants_status ON tenants (status);

CREATE TRIGGER trg_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

ALTER TABLE projects
    ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM projects WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION
            'Existing projects require an explicit tenant migration before SaaS V2 can continue';
    END IF;
END;
$$;

ALTER TABLE projects ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE projects ADD CONSTRAINT ux_projects_id_tenant UNIQUE (id, tenant_id);
DROP INDEX ux_projects_code_ci;
CREATE UNIQUE INDEX ux_projects_tenant_code_ci ON projects (tenant_id, LOWER(code));
CREATE INDEX ix_projects_tenant_status ON projects (tenant_id, status);

ALTER TABLE roles ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE;
UPDATE roles role_row
SET tenant_id = project_row.tenant_id
FROM projects project_row
WHERE role_row.project_id = project_row.id;

ALTER TABLE roles DROP CONSTRAINT roles_scope_ck;
ALTER TABLE roles DROP CONSTRAINT roles_scope_project_ck;
ALTER TABLE roles ADD CONSTRAINT roles_scope_ck
    CHECK (scope IN ('PLATFORM', 'TENANT', 'PROJECT'));
ALTER TABLE roles ADD CONSTRAINT roles_scope_tenant_project_ck CHECK (
    (scope = 'PLATFORM' AND tenant_id IS NULL AND project_id IS NULL)
    OR (scope = 'TENANT' AND tenant_id IS NOT NULL AND project_id IS NULL)
    OR (scope = 'PROJECT' AND tenant_id IS NOT NULL AND project_id IS NOT NULL)
);
ALTER TABLE roles ADD CONSTRAINT fk_roles_project_tenant
    FOREIGN KEY (project_id, tenant_id) REFERENCES projects(id, tenant_id) ON DELETE CASCADE;

CREATE UNIQUE INDEX ux_roles_tenant_code_ci
    ON roles (tenant_id, LOWER(code)) WHERE scope = 'TENANT';
CREATE INDEX ix_roles_tenant_scope ON roles (tenant_id, scope);

CREATE TABLE tenant_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES iam_users(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL DEFAULT 'INVITED',
    invited_by_user_id UUID REFERENCES iam_users(id) ON DELETE SET NULL,
    invited_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    joined_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tenant_members_status_ck CHECK (
        status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REMOVED')
    ),
    CONSTRAINT tenant_members_joined_ck CHECK (
        status <> 'ACTIVE' OR joined_at IS NOT NULL
    ),
    CONSTRAINT ux_tenant_members_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE INDEX ix_tenant_members_user_status ON tenant_members (user_id, status);
CREATE INDEX ix_tenant_members_tenant_status ON tenant_members (tenant_id, status);

CREATE TRIGGER trg_tenant_members_updated_at
    BEFORE UPDATE ON tenant_members
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE tenant_member_roles (
    tenant_member_id UUID NOT NULL REFERENCES tenant_members(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (tenant_member_id, role_id)
);

CREATE OR REPLACE FUNCTION validate_tenant_member_role_assignment()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM tenant_members member_row
        JOIN roles role_row ON role_row.id = NEW.role_id
        WHERE member_row.id = NEW.tenant_member_id
          AND role_row.scope = 'TENANT'
          AND role_row.tenant_id = member_row.tenant_id
    ) THEN
        RAISE EXCEPTION 'A tenant member role must belong to the same tenant';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_tenant_member_role
    BEFORE INSERT OR UPDATE ON tenant_member_roles
    FOR EACH ROW EXECUTE FUNCTION validate_tenant_member_role_assignment();

CREATE OR REPLACE FUNCTION validate_project_member_tenant()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM projects project_row
        JOIN tenant_members tenant_member
          ON tenant_member.tenant_id = project_row.tenant_id
         AND tenant_member.user_id = NEW.user_id
        WHERE project_row.id = NEW.project_id
          AND tenant_member.status IN ('INVITED', 'ACTIVE', 'SUSPENDED')
    ) THEN
        RAISE EXCEPTION 'A project member must first belong to the project tenant';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_project_member_tenant
    BEFORE INSERT OR UPDATE OF project_id, user_id ON project_members
    FOR EACH ROW EXECUTE FUNCTION validate_project_member_tenant();

CREATE OR REPLACE FUNCTION validate_project_member_role_assignment()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM project_members member_row
        JOIN projects project_row ON project_row.id = member_row.project_id
        JOIN roles role_row ON role_row.id = NEW.role_id
        WHERE member_row.id = NEW.project_member_id
          AND role_row.scope = 'PROJECT'
          AND role_row.project_id = member_row.project_id
          AND role_row.tenant_id = project_row.tenant_id
    ) THEN
        RAISE EXCEPTION 'A project member role must belong to the same tenant and project';
    END IF;
    RETURN NEW;
END;
$$;

ALTER TABLE audit_logs
    ADD COLUMN tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL;
CREATE INDEX ix_audit_logs_tenant_created ON audit_logs (tenant_id, created_at DESC);

CREATE OR REPLACE FUNCTION validate_audit_tenant_project()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.project_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM projects
        WHERE id = NEW.project_id AND tenant_id = NEW.tenant_id
    ) THEN
        RAISE EXCEPTION 'Audit tenant and project must match';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_audit_tenant_project
    BEFORE INSERT ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION validate_audit_tenant_project();

-- Stable permission catalog. Role display names may be localized, but these
-- codes are API contracts and never derive from front-end labels.
INSERT INTO permissions (code, name, module, description) VALUES
    ('platform-user:read', 'Read platform users', 'platform', 'Read global user metadata'),
    ('platform-user:manage', 'Manage platform users', 'platform', 'Create, suspend and manage non-Root users'),
    ('platform-tenant:read', 'Read tenants', 'platform', 'Read tenant metadata'),
    ('platform-tenant:manage', 'Manage tenants', 'platform', 'Create, update, archive and restore tenants'),
    ('platform-role:read', 'Read platform roles', 'platform', 'Read platform role definitions'),
    ('platform-role:manage', 'Manage platform roles', 'platform', 'Manage non-Root platform roles and assignments'),
    ('platform-broker:read', 'Read platform brokers', 'mqtt', 'Read redacted platform broker configuration'),
    ('platform-broker:manage', 'Manage platform brokers', 'mqtt', 'Manage platform-scoped broker configuration'),
    ('platform-broker:test', 'Test platform brokers', 'mqtt', 'Test a platform-scoped broker connection'),
    ('platform-audit:read', 'Read platform audit', 'audit', 'Read platform-level audit records'),
    ('tenant:read', 'Read tenant', 'tenant', 'Read the current tenant'),
    ('tenant:update', 'Update tenant', 'tenant', 'Update the current tenant'),
    ('tenant-member:read', 'Read tenant members', 'tenant', 'Read members in the current tenant'),
    ('tenant-member:invite', 'Invite tenant members', 'tenant', 'Invite or approve a tenant member'),
    ('tenant-member:assign', 'Assign tenant roles', 'tenant', 'Assign delegable tenant roles'),
    ('tenant-member:remove', 'Remove tenant members', 'tenant', 'Suspend or remove a tenant member'),
    ('tenant-role:read', 'Read tenant roles', 'tenant', 'Read tenant role definitions'),
    ('tenant-role:manage', 'Manage tenant roles', 'tenant', 'Manage tenant-scoped role definitions'),
    ('tenant-project:read', 'Read tenant projects', 'project', 'Read projects in the current tenant'),
    ('tenant-project:create', 'Create tenant projects', 'project', 'Create a project in the current tenant'),
    ('tenant-project:manage', 'Manage tenant projects', 'project', 'Update, archive and restore tenant projects'),
    ('tenant-broker:read', 'Read tenant brokers', 'mqtt', 'Read redacted tenant broker configuration'),
    ('tenant-broker:manage', 'Manage tenant brokers', 'mqtt', 'Manage tenant-scoped broker configuration'),
    ('tenant-broker:test', 'Test tenant brokers', 'mqtt', 'Test a tenant-scoped broker connection'),
    ('tenant-audit:read', 'Read tenant audit', 'audit', 'Read tenant audit records'),
    ('project:read', 'Read project', 'project', 'Read the selected project'),
    ('project:update', 'Update project', 'project', 'Update the selected project'),
    ('project-member:read', 'Read project members', 'project', 'Read selected-project members'),
    ('project-member:invite', 'Invite project members', 'project', 'Invite an existing tenant member to a project'),
    ('project-member:assign', 'Assign project roles', 'project', 'Assign delegable project roles'),
    ('project-member:remove', 'Remove project members', 'project', 'Suspend or remove a project member'),
    ('project-role:read', 'Read project roles', 'project', 'Read selected-project role definitions'),
    ('project-role:manage', 'Manage project roles', 'project', 'Manage selected-project roles'),
    ('mqtt-connection:read', 'Read MQTT connections', 'mqtt', 'Read redacted project MQTT connections'),
    ('mqtt-connection:configure', 'Configure MQTT connections', 'mqtt', 'Create and update project MQTT connections'),
    ('mqtt-connection:test', 'Test MQTT connections', 'mqtt', 'Test a project MQTT connection'),
    ('mqtt-topic-route:read', 'Read MQTT topic routes', 'mqtt', 'Read project MQTT topic routes'),
    ('mqtt-topic-route:configure', 'Configure MQTT topic routes', 'mqtt', 'Create and update project MQTT topic routes'),
    ('mqtt-message:read', 'Read MQTT messages', 'mqtt', 'Read authorized project MQTT message metadata'),
    ('device-command:publish', 'Publish device commands', 'mqtt', 'Publish validated and audited project device commands')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO roles (code, name, scope, system_role, protected_role)
SELECT 'SUPER_ADMIN', 'Super Administrator', 'PLATFORM', TRUE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM roles
    WHERE scope = 'PLATFORM' AND LOWER(code) = LOWER('SUPER_ADMIN')
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role_row.id, permission_row.id
FROM roles role_row
CROSS JOIN permissions permission_row
WHERE role_row.scope = 'PLATFORM'
  AND LOWER(role_row.code) = LOWER('SUPER_ADMIN')
ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION bump_user_authorization_version(target_user_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE iam_users
    SET authorization_version = authorization_version + 1
    WHERE id = target_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION bump_assignment_user_authorization_version()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    old_user_id UUID;
    new_user_id UUID;
BEGIN
    IF TG_TABLE_NAME = 'user_platform_roles' THEN
        IF TG_OP <> 'INSERT' THEN old_user_id := OLD.user_id; END IF;
        IF TG_OP <> 'DELETE' THEN new_user_id := NEW.user_id; END IF;
    ELSIF TG_TABLE_NAME = 'tenant_members' OR TG_TABLE_NAME = 'project_members' THEN
        IF TG_OP <> 'INSERT' THEN old_user_id := OLD.user_id; END IF;
        IF TG_OP <> 'DELETE' THEN new_user_id := NEW.user_id; END IF;
    ELSIF TG_TABLE_NAME = 'tenant_member_roles' THEN
        IF TG_OP <> 'INSERT' THEN
            SELECT user_id INTO old_user_id FROM tenant_members WHERE id = OLD.tenant_member_id;
        END IF;
        IF TG_OP <> 'DELETE' THEN
            SELECT user_id INTO new_user_id FROM tenant_members WHERE id = NEW.tenant_member_id;
        END IF;
    ELSIF TG_TABLE_NAME = 'project_member_roles' THEN
        IF TG_OP <> 'INSERT' THEN
            SELECT user_id INTO old_user_id FROM project_members WHERE id = OLD.project_member_id;
        END IF;
        IF TG_OP <> 'DELETE' THEN
            SELECT user_id INTO new_user_id FROM project_members WHERE id = NEW.project_member_id;
        END IF;
    END IF;

    IF old_user_id IS NOT NULL THEN
        PERFORM bump_user_authorization_version(old_user_id);
    END IF;
    IF new_user_id IS NOT NULL AND new_user_id IS DISTINCT FROM old_user_id THEN
        PERFORM bump_user_authorization_version(new_user_id);
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_user_platform_roles_auth_version
    AFTER INSERT OR UPDATE OR DELETE ON user_platform_roles
    FOR EACH ROW EXECUTE FUNCTION bump_assignment_user_authorization_version();
CREATE TRIGGER trg_tenant_members_auth_version
    AFTER INSERT OR UPDATE OR DELETE ON tenant_members
    FOR EACH ROW EXECUTE FUNCTION bump_assignment_user_authorization_version();
CREATE TRIGGER trg_tenant_member_roles_auth_version
    AFTER INSERT OR UPDATE OR DELETE ON tenant_member_roles
    FOR EACH ROW EXECUTE FUNCTION bump_assignment_user_authorization_version();
CREATE TRIGGER trg_project_members_auth_version
    AFTER INSERT OR UPDATE OR DELETE ON project_members
    FOR EACH ROW EXECUTE FUNCTION bump_assignment_user_authorization_version();
CREATE TRIGGER trg_project_member_roles_auth_version
    AFTER INSERT OR UPDATE OR DELETE ON project_member_roles
    FOR EACH ROW EXECUTE FUNCTION bump_assignment_user_authorization_version();

CREATE OR REPLACE FUNCTION bump_role_permission_assignees()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    changed_role_id UUID := COALESCE(NEW.role_id, OLD.role_id);
    assigned_user_id UUID;
BEGIN
    FOR assigned_user_id IN
        SELECT user_id FROM user_platform_roles WHERE role_id = changed_role_id
        UNION
        SELECT member_row.user_id
        FROM tenant_member_roles assignment
        JOIN tenant_members member_row ON member_row.id = assignment.tenant_member_id
        WHERE assignment.role_id = changed_role_id
        UNION
        SELECT member_row.user_id
        FROM project_member_roles assignment
        JOIN project_members member_row ON member_row.id = assignment.project_member_id
        WHERE assignment.role_id = changed_role_id
    LOOP
        PERFORM bump_user_authorization_version(assigned_user_id);
    END LOOP;
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_role_permissions_auth_version
    AFTER INSERT OR UPDATE OR DELETE ON role_permissions
    FOR EACH ROW EXECUTE FUNCTION bump_role_permission_assignees();

CREATE OR REPLACE FUNCTION protect_platform_system_role()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.protected_role THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'Protected platform roles cannot be deleted';
        END IF;
        IF NEW.code <> OLD.code
            OR NEW.scope <> OLD.scope
            OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
            OR NEW.project_id IS DISTINCT FROM OLD.project_id
            OR NOT NEW.system_role
            OR NOT NEW.protected_role THEN
            RAISE EXCEPTION 'Protected platform role identity cannot be changed';
        END IF;
    END IF;
    IF TG_OP = 'UPDATE' THEN RETURN NEW; END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_protect_platform_system_role
    BEFORE UPDATE OR DELETE ON roles
    FOR EACH ROW EXECUTE FUNCTION protect_platform_system_role();

CREATE OR REPLACE FUNCTION prevent_last_super_admin_removal()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    target_role_id UUID := OLD.role_id;
BEGIN
    IF EXISTS (
        SELECT 1 FROM roles
        WHERE id = target_role_id
          AND scope = 'PLATFORM'
          AND LOWER(code) = LOWER('SUPER_ADMIN')
    ) AND NOT EXISTS (
        SELECT 1
        FROM user_platform_roles assignment
        JOIN iam_users user_row ON user_row.id = assignment.user_id
        WHERE assignment.role_id = target_role_id
          AND assignment.user_id <> OLD.user_id
          AND user_row.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'The last active Super Administrator assignment cannot be removed';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_prevent_last_super_admin_removal
    BEFORE DELETE ON user_platform_roles
    FOR EACH ROW EXECUTE FUNCTION prevent_last_super_admin_removal();

-- Transaction-local RLS context. Values are set only by authenticated server
-- code; no value is accepted from a request header or request body.
CREATE OR REPLACE FUNCTION app_current_user_id()
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('app.current_user_id', TRUE), '')::UUID;
$$;

CREATE OR REPLACE FUNCTION app_current_tenant_id()
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('app.current_tenant_id', TRUE), '')::UUID;
$$;

CREATE OR REPLACE FUNCTION app_current_project_id()
RETURNS UUID
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('app.current_project_id', TRUE), '')::UUID;
$$;

CREATE OR REPLACE FUNCTION app_is_platform_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE(current_setting('app.is_platform_admin', TRUE), 'false') = 'true';
$$;

CREATE OR REPLACE FUNCTION app_has_tenant_access(row_tenant_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT app_is_platform_admin() OR row_tenant_id = app_current_tenant_id();
$$;

CREATE OR REPLACE FUNCTION app_has_project_access(row_project_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT app_is_platform_admin() OR row_project_id = app_current_project_id();
$$;

CREATE OR REPLACE FUNCTION app_has_project_access(row_tenant_id UUID, row_project_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
AS $$
    SELECT app_is_platform_admin()
        OR (
            row_tenant_id = app_current_tenant_id()
            AND row_project_id = app_current_project_id()
        );
$$;

ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenants_isolation ON tenants
    USING (app_has_tenant_access(id))
    WITH CHECK (app_has_tenant_access(id));

ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects FORCE ROW LEVEL SECURITY;
CREATE POLICY projects_isolation ON projects
    USING (app_has_tenant_access(tenant_id))
    WITH CHECK (app_has_tenant_access(tenant_id));

ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;
CREATE POLICY roles_isolation ON roles
    USING (scope = 'PLATFORM' OR app_has_tenant_access(tenant_id))
    WITH CHECK (app_is_platform_admin() OR app_has_tenant_access(tenant_id));

ALTER TABLE tenant_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_members FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_members_isolation ON tenant_members
    USING (
        app_is_platform_admin()
        OR user_id = app_current_user_id()
        OR tenant_id = app_current_tenant_id()
    )
    WITH CHECK (app_is_platform_admin() OR tenant_id = app_current_tenant_id());

ALTER TABLE tenant_member_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_member_roles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_member_roles_isolation ON tenant_member_roles
    USING (
        EXISTS (
            SELECT 1 FROM tenant_members member_row
            WHERE member_row.id = tenant_member_id
              AND (
                  app_is_platform_admin()
                  OR member_row.user_id = app_current_user_id()
                  OR member_row.tenant_id = app_current_tenant_id()
              )
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM tenant_members member_row
            WHERE member_row.id = tenant_member_id
              AND (app_is_platform_admin() OR member_row.tenant_id = app_current_tenant_id())
        )
    );

DROP POLICY project_members_isolation ON project_members;
CREATE POLICY project_members_isolation ON project_members
    USING (
        app_is_platform_admin()
        OR EXISTS (
            SELECT 1 FROM projects project_row
            WHERE project_row.id = project_id
              AND project_row.tenant_id = app_current_tenant_id()
              AND (
                  project_id = app_current_project_id()
                  OR user_id = app_current_user_id()
              )
        )
    )
    WITH CHECK (
        app_is_platform_admin()
        OR EXISTS (
            SELECT 1 FROM projects project_row
            WHERE project_row.id = project_id
              AND app_has_project_access(project_row.tenant_id, project_id)
        )
    );

ALTER TABLE project_member_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_member_roles FORCE ROW LEVEL SECURITY;
CREATE POLICY project_member_roles_isolation ON project_member_roles
    USING (
        EXISTS (
            SELECT 1 FROM project_members member_row
            WHERE member_row.id = project_member_id
              AND (
                  app_is_platform_admin()
                  OR member_row.user_id = app_current_user_id()
                  OR member_row.project_id = app_current_project_id()
              )
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM project_members member_row
            WHERE member_row.id = project_member_id
              AND (app_is_platform_admin() OR member_row.project_id = app_current_project_id())
        )
    );

ALTER TABLE role_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_permissions FORCE ROW LEVEL SECURITY;
CREATE POLICY role_permissions_isolation ON role_permissions
    USING (
        EXISTS (
            SELECT 1 FROM roles role_row
            WHERE role_row.id = role_id
              AND (role_row.scope = 'PLATFORM' OR app_has_tenant_access(role_row.tenant_id))
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM roles role_row
            WHERE role_row.id = role_id
              AND (app_is_platform_admin() OR app_has_tenant_access(role_row.tenant_id))
        )
    );

DROP POLICY audit_logs_isolation ON audit_logs;
CREATE POLICY audit_logs_isolation ON audit_logs
    USING (
        app_is_platform_admin()
        OR (
            tenant_id = app_current_tenant_id()
            AND (project_id IS NULL OR project_id = app_current_project_id())
        )
    )
    WITH CHECK (
        app_is_platform_admin()
        OR (
            tenant_id = app_current_tenant_id()
            AND (project_id IS NULL OR project_id = app_current_project_id())
        )
    );
