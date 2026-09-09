package com.assetmanagement.project.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectMemberView(
        UUID id,
        UUID userId,
        String username,
        String displayName,
        String status,
        List<String> roles,
        Instant joinedAt
) {
}
