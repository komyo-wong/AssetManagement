package com.assetmanagement.project.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProjectRoleCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 120) String name,
        List<String> permissionCodes
) {
}
