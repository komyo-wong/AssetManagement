package com.assetmanagement.tenant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TenantMemberInviteRequest(
        @NotBlank @Size(max = 80) String username,
        @Size(max = 254) String email,
        @Size(max = 128) String password,
        @Size(max = 120) String displayName,
        List<String> roleCodes
) {
}
