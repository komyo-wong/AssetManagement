ALTER TABLE geotag_cloud_devices
    ADD COLUMN history_pulled_to TIMESTAMPTZ,
    ADD COLUMN history_pulled_at TIMESTAMPTZ,
    ADD COLUMN history_backoff_until TIMESTAMPTZ,
    ADD COLUMN history_fail_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_geotag_cloud_devices_history_pull
    ON geotag_cloud_devices (history_pulled_at ASC NULLS FIRST);
