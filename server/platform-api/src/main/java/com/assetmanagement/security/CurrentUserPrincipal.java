package com.assetmanagement.security;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

public record CurrentUserPrincipal(
        UUID userId,
        UUID sessionId,
        String username,
        String email,
        String displayName,
        String preferredLocale,
        boolean rootAccount,
        long authorizationVersion,
        Set<String> platformRoleCodes,
        Set<String> platformPermissionCodes,
        Set<UUID> tenantIdsClaim
) implements Principal {
    public CurrentUserPrincipal {
        platformRoleCodes = Set.copyOf(platformRoleCodes);
        platformPermissionCodes = Set.copyOf(platformPermissionCodes);
        tenantIdsClaim = Set.copyOf(tenantIdsClaim);
    }

    @Override
    public String getName() {
        return username;
    }

    public boolean hasPlatformPermission(String permissionCode) {
        return rootAccount || platformPermissionCodes.contains(permissionCode);
    }
}
