-- Optional GeoTag webhook: public host/port for the callback URL.

ALTER TABLE platform_geotag_settings
    ADD COLUMN IF NOT EXISTS webhook_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS webhook_host VARCHAR(200),
    ADD COLUMN IF NOT EXISTS webhook_port INT,
    ADD COLUMN IF NOT EXISTS webhook_https BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_webhook_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_webhook_probe_ok BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_webhook_probe_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_webhook_probe_message VARCHAR(500);

UPDATE platform_geotag_settings
SET webhook_port = 80
WHERE webhook_port IS NULL;
