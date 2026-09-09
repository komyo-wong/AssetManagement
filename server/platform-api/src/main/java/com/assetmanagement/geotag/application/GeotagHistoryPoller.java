package com.assetmanagement.geotag.application;

import com.assetmanagement.geotag.domain.GeotagCloudDevice;
import com.assetmanagement.geotag.domain.PlatformGeotagSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
public class GeotagHistoryPoller {

    private static final Logger log = LoggerFactory.getLogger(GeotagHistoryPoller.class);

    private final GeotagSettingsService geotagSettingsService;
    private final GeotagCatalogService geotagCatalogService;
    private final GeotagHistorySyncService historySyncService;

    public GeotagHistoryPoller(
            GeotagSettingsService geotagSettingsService,
            GeotagCatalogService geotagCatalogService,
            GeotagHistorySyncService historySyncService
    ) {
        this.geotagSettingsService = geotagSettingsService;
        this.geotagCatalogService = geotagCatalogService;
        this.historySyncService = historySyncService;
    }

    @Scheduled(fixedDelayString = "${app.geotag.history-poll-interval:60s}")
    public void poll() {
        try {
            PlatformGeotagSettings settings = geotagSettingsService.requireSettings();
            if (!settings.isEnabled() || settings.isWebhookEnabled() || settings.isMock()) {
                return;
            }
            Set<String> visible = geotagCatalogService.listVisibleNormalizedSns();
            Instant now = Instant.now();
            List<GeotagCloudDevice> due = historySyncService.pickDue(visible, now);
            for (GeotagCloudDevice device : due) {
                historySyncService.pullOne(device.getId());
            }
        } catch (RuntimeException exception) {
            log.debug("GeoTag history poll skipped: {}", exception.toString());
        }
    }
}
