package com.assetmanagement.project.api;

import java.time.Instant;
import java.util.UUID;

public record ProjectView(
        UUID id,
        long version,
        UUID tenantId,
        String code,
        String name,
        String description,
        String status,
        String defaultLocale,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
