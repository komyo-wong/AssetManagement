package com.assetmanagement.tenant.api;

import java.util.UUID;

public record PermissionView(
        UUID id,
        String code,
        String name,
        String module,
        String description
) {
}
