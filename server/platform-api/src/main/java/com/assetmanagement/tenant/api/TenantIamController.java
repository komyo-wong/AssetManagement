package com.assetmanagement.tenant.api;

import com.assetmanagement.tenant.application.TenantIamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class TenantIamController {

    private final TenantIamService tenantIamService;

    public TenantIamController(TenantIamService tenantIamService) {
        this.tenantIamService = tenantIamService;
    }

    @GetMapping("/members")
    public List<TenantMemberView> listMembers(@PathVariable UUID tenantId) {
        return tenantIamService.listMembers(tenantId);
    }

    @PostMapping("/members")
    public TenantMemberView inviteMember(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantMemberInviteRequest request
    ) {
        return tenantIamService.inviteMember(tenantId, request);
    }

    @PutMapping("/members/{memberId}/roles")
    public TenantMemberView replaceMemberRoles(
            @PathVariable UUID tenantId,
            @PathVariable UUID memberId,
            @RequestBody MemberRolesUpdateRequest request
    ) {
        return tenantIamService.replaceMemberRoles(tenantId, memberId, request);
    }

    @PostMapping("/members/{memberId}/remove")
    public TenantMemberView removeMember(
            @PathVariable UUID tenantId,
            @PathVariable UUID memberId
    ) {
        return tenantIamService.removeMember(tenantId, memberId);
    }

    @GetMapping("/roles")
    public List<TenantRoleView> listRoles(@PathVariable UUID tenantId) {
        return tenantIamService.listRoles(tenantId);
    }

    @PostMapping("/roles")
    public TenantRoleView createRole(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantRoleCreateRequest request
    ) {
        return tenantIamService.createRole(tenantId, request);
    }

    @PutMapping("/roles/{roleId}")
    public TenantRoleView updateRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID roleId,
            @Valid @RequestBody TenantRoleUpdateRequest request
    ) {
        return tenantIamService.updateRole(tenantId, roleId, request);
    }

    @DeleteMapping("/roles/{roleId}")
    public void deleteRole(@PathVariable UUID tenantId, @PathVariable UUID roleId) {
        tenantIamService.deleteRole(tenantId, roleId);
    }

    @GetMapping("/permissions")
    public List<PermissionView> listPermissions(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String module
    ) {
        return tenantIamService.listPermissions(tenantId, module);
    }
}
