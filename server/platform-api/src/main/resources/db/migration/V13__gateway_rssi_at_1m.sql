-- Per-gateway 1m RSSI calibration for ranging (ESP32 receiver side).
ALTER TABLE gateways
    ADD COLUMN IF NOT EXISTS rssi_at_1m INTEGER;

UPDATE gateways
SET rssi_at_1m = -61
WHERE rssi_at_1m IS NULL;
