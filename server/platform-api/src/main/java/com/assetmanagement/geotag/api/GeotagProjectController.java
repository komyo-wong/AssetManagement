package com.assetmanagement.geotag.api;

import com.assetmanagement.geotag.application.GeotagCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/projects/{projectId}/geotag")
public class GeotagProjectController {

    private final GeotagCatalogService geotagCatalogService;

    public GeotagProjectController(GeotagCatalogService geotagCatalogService) {
        this.geotagCatalogService = geotagCatalogService;
    }

    @GetMapping("/devices")
    public Map<String, Object> devices(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return geotagCatalogService.listDevices(tenantId, projectId);
    }

    @GetMapping("/positions")
    public Map<String, Object> positions(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return geotagCatalogService.listPositions(tenantId, projectId);
    }

    @GetMapping("/tracks")
    public Map<String, Object> tracks(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @RequestParam String sn,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return geotagCatalogService.listTracks(tenantId, projectId, sn, from, to);
    }
}
