package com.assetmanagement.tenant.api;

import java.util.List;
import java.util.UUID;

public record TenantRoleView(
        UUID id,
        String code,
        String name,
        String scope,
        List<String> permissionCodes,
        boolean systemRole,
        boolean protectedRole
) {
}
