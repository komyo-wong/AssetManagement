package com.assetmanagement.business.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ResourceDtos {
    private ResourceDtos() {}

    public record NamedResource(
            UUID id, long version, UUID tenantId, UUID projectId,
            String code, String name, String status,
            Instant createdAt, Instant updatedAt,
            Map<String, Object> fields
    ) {}

    public record UpsertRequest(
            String code, String name, String description, String status,
            Map<String, Object> fields
    ) {}

    public record BatchIdsRequest(java.util.List<UUID> ids) {}
}
