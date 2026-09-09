package com.assetmanagement.geotag.api;

import com.assetmanagement.geotag.application.GeotagWebhookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/geotag")
public class PublicGeotagWebhookController {

    private final GeotagWebhookService geotagWebhookService;

    public PublicGeotagWebhookController(GeotagWebhookService geotagWebhookService) {
        this.geotagWebhookService = geotagWebhookService;
    }

    @GetMapping("/webhook")
    public Map<String, Object> probe() {
        return Map.of("ok", true, "probe", true);
    }

    @PostMapping("/webhook")
    public Map<String, Object> webhook(@RequestBody(required = false) Map<String, Object> body) {
        return geotagWebhookService.ingestPublic(body == null ? Map.of() : body);
    }
}
