-- Keep Scan Response separately so battery re-parse can use both adv + srp.
ALTER TABLE beacons
    ADD COLUMN IF NOT EXISTS last_srp_raw TEXT;
