package com.assetmanagement.geotag.api;

import com.assetmanagement.geotag.application.GeotagSettingsService;
import com.assetmanagement.geotag.application.GeotagWebhookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/geotag-settings")
public class PlatformGeotagController {

    private final GeotagSettingsService geotagSettingsService;
    private final GeotagWebhookService geotagWebhookService;

    public PlatformGeotagController(
            GeotagSettingsService geotagSettingsService,
            GeotagWebhookService geotagWebhookService
    ) {
        this.geotagSettingsService = geotagSettingsService;
        this.geotagWebhookService = geotagWebhookService;
    }

    @GetMapping
    public Map<String, Object> get() {
        return geotagSettingsService.getSettings();
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
        return geotagSettingsService.updateSettings(body);
    }

    @PostMapping("/test")
    public Map<String, Object> test() {
        return geotagSettingsService.testConnection();
    }

    @GetMapping("/public-ip")
    public Map<String, Object> publicIp() {
        return geotagSettingsService.detectedPublicIp();
    }

    @PostMapping("/test-webhook")
    public Map<String, Object> testWebhook(@RequestBody(required = false) Map<String, Object> body) {
        return geotagSettingsService.testWebhook(body == null ? Map.of() : body);
    }

    @PostMapping("/rotate-keys")
    public Map<String, Object> rotateKeys() {
        return geotagSettingsService.rotateKeys();
    }

    @PostMapping("/simulate-point")
    public Map<String, Object> simulate(@RequestBody Map<String, Object> body) {
        return geotagWebhookService.simulate(body);
    }
}
