-- Default 1m calibration RSSI for distance estimation.
UPDATE beacons
SET rssi_at_1m = -61
WHERE rssi_at_1m IS NULL;
