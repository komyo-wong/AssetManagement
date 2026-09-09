package com.assetmanagement.branding.api;

import com.assetmanagement.branding.application.PlatformBrandingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Unauthenticated branding snapshot for login / document chrome. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicBrandingController {

    private final PlatformBrandingService brandingService;

    public PublicBrandingController(PlatformBrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping("/branding")
    public Map<String, Object> branding() {
        return brandingService.getPublic();
    }
}
