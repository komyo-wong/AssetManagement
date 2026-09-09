-- Gateway belongs to a zone; map association syncs from zone.map_id.

ALTER TABLE gateways
    ADD COLUMN IF NOT EXISTS zone_id UUID;

ALTER TABLE gateways
    DROP CONSTRAINT IF EXISTS fk_gateways_zone;

ALTER TABLE gateways
    ADD CONSTRAINT fk_gateways_zone FOREIGN KEY (zone_id) REFERENCES zones(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS ix_gateways_zone ON gateways (zone_id);
CREATE INDEX IF NOT EXISTS ix_zones_map ON zones (map_id);
