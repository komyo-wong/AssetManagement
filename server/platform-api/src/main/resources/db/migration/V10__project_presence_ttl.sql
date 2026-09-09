-- Project-level presence TTL for gateway / beacon (asset) online judgment.

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS gateway_online_ttl_seconds INTEGER NOT NULL DEFAULT 90,
    ADD COLUMN IF NOT EXISTS beacon_online_ttl_seconds INTEGER NOT NULL DEFAULT 300;

COMMENT ON COLUMN projects.gateway_online_ttl_seconds IS 'Seconds without gateway presence uplink before OFFLINE';
COMMENT ON COLUMN projects.beacon_online_ttl_seconds IS 'Seconds without beacon scan before asset/beacon OFFLINE';
