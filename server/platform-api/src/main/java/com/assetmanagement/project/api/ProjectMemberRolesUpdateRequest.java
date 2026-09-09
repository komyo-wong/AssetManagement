package com.assetmanagement.project.api;

import java.util.List;

public record ProjectMemberRolesUpdateRequest(List<String> roleCodes) {
}
