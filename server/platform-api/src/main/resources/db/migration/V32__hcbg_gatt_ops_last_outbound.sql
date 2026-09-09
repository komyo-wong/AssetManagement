-- Wait for each buzzer write to be MQTT-SENT before enqueueing the next pulse.
ALTER TABLE hcbg_gatt_ops
    ADD COLUMN last_outbound_id UUID;
