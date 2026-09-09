package com.assetmanagement.license;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/ops/license")
public class PlatformLicenseController {

    private final PlatformLicenseService platformLicenseService;

    public PlatformLicenseController(PlatformLicenseService platformLicenseService) {
        this.platformLicenseService = platformLicenseService;
    }

    @GetMapping
    public Map<String, Object> status() {
        return platformLicenseService.status();
    }

    @PutMapping
    public Map<String, Object> activate(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("token");
        return platformLicenseService.activate(raw == null ? "" : String.valueOf(raw));
    }
}
