package com.assetmanagement.project.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectUpsertRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @Size(max = 16) String defaultLocale
) {
}
