package com.assetmanagement.dashboard.api;

import com.assetmanagement.dashboard.application.DashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/projects/{projectId}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryView summary(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return dashboardService.summary(tenantId, projectId);
    }
}
