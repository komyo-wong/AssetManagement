-- Timed HCBG buzzer pulses: write 01, wait, write 00 (batched writes are inaudible).
ALTER TABLE hcbg_gatt_ops
    ADD COLUMN char_handle INT,
    ADD COLUMN pulses_done INT NOT NULL DEFAULT 0,
    ADD COLUMN waiting_off BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN next_action_at TIMESTAMPTZ;

CREATE INDEX ix_hcbg_gatt_ops_due
    ON hcbg_gatt_ops (status, next_action_at)
    WHERE status = 'PENDING' AND next_action_at IS NOT NULL;
