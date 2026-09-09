-- Selected gateways for timed inventory roll-call.

ALTER TABLE inventory_sessions
    ADD COLUMN IF NOT EXISTS gateway_json TEXT;
