package com.assetmanagement.geotag.application;

import com.assetmanagement.geotag.GeotagCoords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class GeotagReverseGeocoder {

    private static final Logger log = LoggerFactory.getLogger(GeotagReverseGeocoder.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public GeotagReverseGeocoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String lookup(double lat, double lng) {
        if (!valid(lat, lng)) {
            return null;
        }
        GeotagCoords.Point wgs = GeotagCoords.toWgs84(lat, lng);
        String address = nominatim(wgs.lat(), wgs.lng());
        if (address != null) {
            return clip(address, 500);
        }
        return clip(bigDataCloud(wgs.lat(), wgs.lng()), 500);
    }

    private String nominatim(double lat, double lng) {
        String url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=16&addressdetails=0"
                + "&accept-language=zh-CN"
                + "&lat=" + encode(lat)
                + "&lon=" + encode(lng);
        JsonNode root = get(url, "AssetManagement/1.0.7 (geotag reverse-geocode)");
        if (root == null) {
            return null;
        }
        return text(root, "display_name");
    }

    private String bigDataCloud(double lat, double lng) {
        String url = "https://api.bigdatacloud.net/data/reverse-geocode-client"
                + "?latitude=" + encode(lat)
                + "&longitude=" + encode(lng)
                + "&localityLanguage=zh";
        JsonNode root = get(url, "AssetManagement/1.0.7");
        if (root == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        addPart(parts, text(root, "principalSubdivision"));
        addPart(parts, text(root, "city"));
        addPart(parts, text(root, "locality"));
        addPart(parts, text(root, "plusCode"));
        if (parts.isEmpty()) {
            return text(root, "formatted");
        }
        return String.join(" ", parts);
    }

    private JsonNode get(String url, String userAgent) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                return null;
            }
            return objectMapper.readTree(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
            log.debug("GeoTag reverse geocode skipped: {}", exception.getMessage());
            return null;
        }
    }

    private static boolean valid(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    private static String encode(double value) {
        return URLEncoder.encode(Double.toString(value), StandardCharsets.UTF_8);
    }

    private static void addPart(List<String> parts, String value) {
        if (value != null && !parts.contains(value)) {
            parts.add(value);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
