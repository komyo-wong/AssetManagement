-- Per-gateway MQTT username/password for device provisioning (letters and digits only).
ALTER TABLE gateways
    ADD COLUMN IF NOT EXISTS mqtt_password VARCHAR(64);
