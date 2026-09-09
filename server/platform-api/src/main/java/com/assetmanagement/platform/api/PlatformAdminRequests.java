package com.assetmanagement.platform.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class PlatformAdminRequests {

    private PlatformAdminRequests() {
    }

    public record CreateUserRequest(
            @NotBlank(message = "username is required") @Size(max = 80, message = "username is too long") String username,
            @NotBlank(message = "email is required") @Size(max = 254, message = "email is too long") String email,
            @NotBlank(message = "password is required") @Size(min = 8, max = 128, message = "password must be 8-128 characters") String password,
            @Size(max = 120, message = "displayName is too long") String displayName,
            List<String> roleCodes
    ) {
    }

    public record UpdateUserRequest(
            @Size(max = 120, message = "displayName is too long") String displayName,
            @Size(max = 254, message = "email is too long") String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "password is required") @Size(min = 8, max = 128, message = "password must be 8-128 characters") String password
    ) {
    }

    public record ReplaceUserRolesRequest(
            List<String> roleCodes
    ) {
    }

    public record CreateRoleRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 120) String name,
            List<String> permissionCodes
    ) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 120) String name,
            List<String> permissionCodes
    ) {
    }
}
