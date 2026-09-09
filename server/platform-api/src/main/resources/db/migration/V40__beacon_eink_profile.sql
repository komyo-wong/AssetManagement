-- GeoTag panel family: elnk (128×250 BWR) vs za25 (ZA25GM2D 200×300 BWRY).
-- NULL = capable but not yet distinguished (do not assume elnk).

ALTER TABLE beacons
    ADD COLUMN IF NOT EXISTS eink_profile VARCHAR(16);
