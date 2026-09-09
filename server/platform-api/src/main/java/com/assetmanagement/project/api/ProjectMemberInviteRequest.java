package com.assetmanagement.project.api;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ProjectMemberInviteRequest(
        @Size(max = 80) String username,
        UUID userId,
        List<String> roleCodes
) {
}
