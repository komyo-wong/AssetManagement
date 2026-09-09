package com.assetmanagement.geotag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeotagBasemap {

    public static final String OSM = "osm";
    public static final String ESRI = "esri";
    public static final String CARTO = "carto";
    public static final String MAPTILER = "maptiler";

    public static final String CARTO_APPLY_URL = "https://carto.com/basemaps/";
    public static final String MAPTILER_APPLY_URL = "https://cloud.maptiler.com/account/keys/";

    private static final List<String> OSM_TILES = List.of("https://tile.openstreetmap.org/{z}/{x}/{y}.png");
    private static final List<String> ESRI_TILES = List.of(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
    );
    private static final List<String> OSM_TERRAIN = List.of(
            "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"
    );

    private GeotagBasemap() {
    }

    public static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return OSM;
        }
        String value = provider.trim().toLowerCase();
        return switch (value) {
            case OSM, ESRI, CARTO, MAPTILER -> value;
            default -> OSM;
        };
    }

    public static boolean needsKey(String provider) {
        String value = normalizeProvider(provider);
        return CARTO.equals(value) || MAPTILER.equals(value);
    }

    public static String applyUrl(String provider) {
        return switch (normalizeProvider(provider)) {
            case CARTO -> CARTO_APPLY_URL;
            case MAPTILER -> MAPTILER_APPLY_URL;
            default -> null;
        };
    }

    public static Map<String, Object> config(String provider, String apiKey) {
        String requested = normalizeProvider(provider);
        if (CARTO.equals(requested)) {
            return catalog(requested, apiKey, null);
        }
        if (MAPTILER.equals(requested)) {
            return catalog(requested, null, apiKey);
        }
        return catalog(requested, null, null);
    }

    public static Map<String, Object> catalog(String savedProvider, String cartoKey, String maptilerKey) {
        String requested = normalizeProvider(savedProvider);
        Map<String, Object> osm = osmOption();
        Map<String, Object> esri = esriOption();
        Map<String, Object> carto = cartoOption(blankToNull(cartoKey));
        Map<String, Object> maptiler = maptilerOption(blankToNull(maptilerKey));
        List<Map<String, Object>> options = List.of(osm, esri, carto, maptiler);
        Map<String, Object> requestedOption = option(options, requested);
        boolean locked = Boolean.TRUE.equals(requestedOption.get("locked"));
        Map<String, Object> active = locked ? osm : requestedOption;

        Map<String, Object> result = new LinkedHashMap<>(active);
        result.put("provider", active.get("id"));
        result.put("requestedProvider", requested);
        result.put("fallback", locked);
        result.put("needsKey", locked);
        result.put("hasCartoKey", blankToNull(cartoKey) != null);
        result.put("hasMaptilerKey", blankToNull(maptilerKey) != null);
        result.put("applyUrl", applyUrl(requested));
        result.put("options", new ArrayList<>(options));
        return result;
    }

    private static Map<String, Object> option(List<Map<String, Object>> options, String id) {
        for (Map<String, Object> option : options) {
            if (id.equals(option.get("id"))) {
                return option;
            }
        }
        return options.get(0);
    }

    private static Map<String, Object> osmOption() {
        Map<String, Object> option = raster(OSM, OSM_TILES, "© OpenStreetMap contributors", false);
        option.put("supports3d", true);
        option.put("terrain", Map.of(
                "encoding", "terrarium",
                "tileSize", 256,
                "maxzoom", 15,
                "tiles", OSM_TERRAIN
        ));
        return option;
    }

    private static Map<String, Object> esriOption() {
        return raster(ESRI, ESRI_TILES, "Tiles © Esri", false);
    }

    private static Map<String, Object> cartoOption(String key) {
        boolean locked = key == null;
        Map<String, Object> option = raster(
                CARTO,
                locked
                        ? List.of()
                        : List.of("https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png?api_key=" + key),
                "© OpenStreetMap © CARTO",
                locked
        );
        option.put("needsKey", true);
        option.put("hasKey", !locked);
        option.put("applyUrl", CARTO_APPLY_URL);
        return option;
    }

    private static Map<String, Object> maptilerOption(String key) {
        boolean locked = key == null;
        Map<String, Object> option = base(MAPTILER, true);
        option.put("attribution", "© MapTiler © OpenStreetMap");
        option.put("needsKey", true);
        option.put("hasKey", !locked);
        option.put("locked", locked);
        option.put("applyUrl", MAPTILER_APPLY_URL);
        option.put("supports3d", true);
        option.put("tiles", List.of());
        if (!locked) {
            option.put("styleUrl", "https://api.maptiler.com/maps/streets-v2/style.json?key=" + key);
            option.put("terrain", Map.of(
                    "url", "https://api.maptiler.com/tiles/terrain-rgb-v2/tiles.json?key=" + key,
                    "tileSize", 256
            ));
        }
        return option;
    }

    private static Map<String, Object> raster(String id, List<String> tiles, String attribution, boolean locked) {
        Map<String, Object> option = base(id, false);
        option.put("tiles", tiles);
        option.put("attribution", attribution);
        option.put("tileSize", 256);
        option.put("locked", locked);
        return option;
    }

    private static Map<String, Object> base(String id, boolean supports3d) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("id", id);
        option.put("provider", id);
        option.put("supports3d", supports3d);
        option.put("needsKey", false);
        option.put("hasKey", true);
        option.put("locked", false);
        option.put("fallback", false);
        option.put("applyUrl", applyUrl(id));
        return option;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
