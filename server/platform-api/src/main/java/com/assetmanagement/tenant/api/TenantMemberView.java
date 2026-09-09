package com.assetmanagement.tenant.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TenantMemberView(
        UUID id,
        UUID userId,
        String username,
        String displayName,
        String status,
        List<String> roleCodes,
        Instant joinedAt
) {
}
