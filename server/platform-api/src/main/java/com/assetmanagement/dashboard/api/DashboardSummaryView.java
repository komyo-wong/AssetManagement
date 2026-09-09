package com.assetmanagement.dashboard.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardSummaryView(
        UUID projectId,
        Long assetTotal,
        Long onlineGatewayTotal,
        Long activeAlertTotal,
        Double rollCallCoverage,
        String dataAvailability,
        Instant generatedAt,
        List<ReadinessItemView> readiness
) {
    public record ReadinessItemView(
            String key,
            String status,
            String detail
    ) {
    }
}
