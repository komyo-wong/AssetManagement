package com.assetmanagement.project.api;

import java.util.List;
import java.util.UUID;

public record ProjectRoleView(
        UUID id,
        String code,
        String name,
        String scope,
        List<String> permissionCodes,
        boolean systemRole,
        boolean protectedRole
) {
}
