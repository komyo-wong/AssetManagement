package com.assetmanagement.tenant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TenantRoleUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        List<String> permissionCodes
) {
}
