-- Default GeoTag basemap is OpenStreetMap; keep separate keys for CARTO and MapTiler.

ALTER TABLE platform_geotag_settings
    ALTER COLUMN map_provider SET DEFAULT 'osm';

ALTER TABLE platform_geotag_settings
    ADD COLUMN IF NOT EXISTS map_carto_key VARCHAR(200),
    ADD COLUMN IF NOT EXISTS map_maptiler_key VARCHAR(200);

UPDATE platform_geotag_settings
SET map_provider = 'osm'
WHERE map_provider IS NULL OR map_provider = 'esri';

UPDATE platform_geotag_settings
SET map_carto_key = map_api_key
WHERE map_provider = 'carto'
  AND map_api_key IS NOT NULL
  AND map_carto_key IS NULL;

UPDATE platform_geotag_settings
SET map_maptiler_key = map_api_key
WHERE map_provider = 'maptiler'
  AND map_api_key IS NOT NULL
  AND map_maptiler_key IS NULL;
