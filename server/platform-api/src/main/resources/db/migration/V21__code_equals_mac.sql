-- Align business codes with MAC (canonical display form already stored on beacons.mac_address).
UPDATE beacons
SET code = mac_address
WHERE mac_address IS NOT NULL
  AND btrim(mac_address) <> ''
  AND code IS DISTINCT FROM mac_address;

-- Assets with an active beacon binding: code = that beacon's MAC.
UPDATE assets a
SET code = b.mac_address
FROM asset_beacon_bindings abb
JOIN beacons b ON b.id = abb.beacon_id
WHERE abb.asset_id = a.id
  AND abb.active = TRUE
  AND b.mac_address IS NOT NULL
  AND btrim(b.mac_address) <> ''
  AND a.code IS DISTINCT FROM b.mac_address;
