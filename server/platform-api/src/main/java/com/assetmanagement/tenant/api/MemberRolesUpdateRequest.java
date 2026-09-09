package com.assetmanagement.tenant.api;

import java.util.List;

public record MemberRolesUpdateRequest(List<String> roleCodes) {
}
