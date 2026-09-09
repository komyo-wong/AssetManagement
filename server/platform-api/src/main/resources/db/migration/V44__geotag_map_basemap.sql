-- GeoTag map tiles: provider + optional API key (CARTO / MapTiler).

ALTER TABLE platform_geotag_settings
    ADD COLUMN IF NOT EXISTS map_provider VARCHAR(40) NOT NULL DEFAULT 'esri',
    ADD COLUMN IF NOT EXISTS map_api_key VARCHAR(200);
