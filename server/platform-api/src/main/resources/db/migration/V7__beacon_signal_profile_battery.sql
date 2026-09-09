-- Binding protocol (Find My vs classic Beacon) + last battery from Find My OF status byte
ALTER TABLE asset_beacon_bindings
    ADD COLUMN IF NOT EXISTS protocol_type VARCHAR(16) NOT NULL DEFAULT 'BEACON';

ALTER TABLE beacons
    ADD COLUMN IF NOT EXISTS signal_profile VARCHAR(16);

ALTER TABLE beacons
    ADD COLUMN IF NOT EXISTS last_battery_level INTEGER;

ALTER TABLE beacons
    ADD COLUMN IF NOT EXISTS last_battery_label VARCHAR(32);

ALTER TABLE beacons
    ADD COLUMN IF NOT EXISTS last_battery_percent INTEGER;

ALTER TABLE beacons
    ADD COLUMN IF NOT EXISTS last_adv_raw TEXT;
