-- User notification preferences: channels, subscriptions, delivery log.

CREATE TABLE notification_channels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES iam_users(id) ON DELETE CASCADE,
    channel_type VARCHAR(24) NOT NULL,
    address VARCHAR(512) NOT NULL,
    display_name VARCHAR(120),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    config_json TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notification_channels_type_ck CHECK (channel_type IN ('EMAIL', 'WEBHOOK'))
);

CREATE UNIQUE INDEX ux_notification_channels_user_type_addr
    ON notification_channels (user_id, channel_type, lower(address));
CREATE INDEX ix_notification_channels_user ON notification_channels (user_id);

CREATE TRIGGER trg_notification_channels_updated_at
    BEFORE UPDATE ON notification_channels
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE notification_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES iam_users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    channel_id UUID NOT NULL REFERENCES notification_channels(id) ON DELETE CASCADE,
    on_alert BOOLEAN NOT NULL DEFAULT TRUE,
    on_recovery BOOLEAN NOT NULL DEFAULT TRUE,
    min_severity VARCHAR(24) NOT NULL DEFAULT 'WARNING',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_json TEXT,
    throttle_seconds INT,
    rule_types_csv VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notification_subscriptions_sev_ck CHECK (min_severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT fk_notification_subscriptions_project_tenant
        FOREIGN KEY (project_id, tenant_id) REFERENCES projects(id, tenant_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_notification_subscriptions_user_project_channel
    ON notification_subscriptions (user_id, project_id, channel_id);
CREATE INDEX ix_notification_subscriptions_project ON notification_subscriptions (project_id);

CREATE TRIGGER trg_notification_subscriptions_updated_at
    BEFORE UPDATE ON notification_subscriptions
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_event_id UUID REFERENCES alert_events(id) ON DELETE SET NULL,
    subscription_id UUID REFERENCES notification_subscriptions(id) ON DELETE SET NULL,
    channel_id UUID REFERENCES notification_channels(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES iam_users(id) ON DELETE CASCADE,
    event_kind VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    payload_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notification_deliveries_kind_ck CHECK (event_kind IN ('ALERT', 'RECOVERY')),
    CONSTRAINT notification_deliveries_status_ck CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX ix_notification_deliveries_user_created
    ON notification_deliveries (user_id, created_at DESC);
CREATE INDEX ix_notification_deliveries_status
    ON notification_deliveries (status, created_at);

CREATE TRIGGER trg_notification_deliveries_updated_at
    BEFORE UPDATE ON notification_deliveries
    FOR EACH ROW EXECUTE FUNCTION set_row_updated_at();

INSERT INTO permissions (code, name, module, description) VALUES
    ('notification:read', 'Read own notification preferences', 'notification', 'View personal notification channels and subscriptions'),
    ('notification:manage', 'Manage own notification preferences', 'notification', 'Create and update personal notification channels and subscriptions')
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
  AND permission_row.code IN ('notification:read', 'notification:manage')
ON CONFLICT DO NOTHING;
