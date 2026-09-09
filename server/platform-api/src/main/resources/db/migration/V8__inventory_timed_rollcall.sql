-- Inventory becomes timed roll-call: countdown window + scoped assets + live scan hits.

ALTER TABLE inventory_sessions
    ADD COLUMN IF NOT EXISTS window_seconds INTEGER NOT NULL DEFAULT 300,
    ADD COLUMN IF NOT EXISTS ends_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS scope_type VARCHAR(24) NOT NULL DEFAULT 'ALL',
    ADD COLUMN IF NOT EXISTS scope_json TEXT;

ALTER TABLE inventory_sessions
    DROP CONSTRAINT IF EXISTS inventory_sessions_scope_type_ck;
ALTER TABLE inventory_sessions
    ADD CONSTRAINT inventory_sessions_scope_type_ck
        CHECK (scope_type IN ('ALL', 'ASSETS', 'TYPES'));

UPDATE inventory_sessions
   SET ends_at = started_at + make_interval(secs => window_seconds)
 WHERE ends_at IS NULL;

ALTER TABLE inventory_items
    ADD COLUMN IF NOT EXISTS last_rssi INTEGER,
    ADD COLUMN IF NOT EXISTS gateway_id UUID,
    ADD COLUMN IF NOT EXISTS beacon_id UUID;
