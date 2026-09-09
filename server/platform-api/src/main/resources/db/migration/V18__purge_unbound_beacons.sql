-- Drop auto-discovered beacons that were never bound to an asset.
-- Bound beacons are retained. Scan events keep history with beacon_id set NULL.
-- Inactive bindings cascade with the beacon row.

DELETE FROM beacons b
WHERE NOT EXISTS (
    SELECT 1
    FROM asset_beacon_bindings abb
    WHERE abb.beacon_id = b.id
      AND abb.active = TRUE
);
