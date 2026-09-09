package com.assetmanagement.project.api;

import com.assetmanagement.project.api.ProjectMemberInviteRequest;
import com.assetmanagement.project.api.ProjectMemberRolesUpdateRequest;
import com.assetmanagement.project.api.ProjectMemberView;
import com.assetmanagement.project.api.ProjectPresenceSettingsRequest;
import com.assetmanagement.project.api.ProjectPresenceSettingsView;
import com.assetmanagement.project.api.ProjectRoleCreateRequest;
import com.assetmanagement.project.api.ProjectRoleUpdateRequest;
import com.assetmanagement.project.api.ProjectRoleView;
import com.assetmanagement.project.api.ProjectUpsertRequest;
import com.assetmanagement.project.api.ProjectView;
import com.assetmanagement.project.application.ProjectManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/projects")
public class ProjectController {

    private final ProjectManagementService projectManagementService;

    public ProjectController(ProjectManagementService projectManagementService) {
        this.projectManagementService = projectManagementService;
    }

    @GetMapping
    public List<ProjectView> list(@PathVariable UUID tenantId) {
        return projectManagementService.list(tenantId);
    }

    @PostMapping
    public ProjectView create(@PathVariable UUID tenantId, @Valid @RequestBody ProjectUpsertRequest request) {
        return projectManagementService.create(tenantId, request);
    }

    @PutMapping("/{projectId}")
    public ProjectView update(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectUpsertRequest request
    ) {
        return projectManagementService.update(tenantId, projectId, request);
    }

    @PostMapping("/{projectId}/archive")
    public ProjectView archive(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return projectManagementService.archive(tenantId, projectId);
    }

    @GetMapping("/{projectId}/presence-settings")
    public ProjectPresenceSettingsView getPresenceSettings(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId
    ) {
        return projectManagementService.getPresenceSettings(tenantId, projectId);
    }

    @PutMapping("/{projectId}/presence-settings")
    public ProjectPresenceSettingsView updatePresenceSettings(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectPresenceSettingsRequest request
    ) {
        return projectManagementService.updatePresenceSettings(tenantId, projectId, request);
    }

    @GetMapping("/{projectId}/members")
    public List<ProjectMemberView> members(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return projectManagementService.listMembers(tenantId, projectId);
    }

    @PostMapping("/{projectId}/members")
    public ProjectMemberView inviteMember(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @RequestBody ProjectMemberInviteRequest request
    ) {
        return projectManagementService.inviteMember(tenantId, projectId, request);
    }

    @PutMapping("/{projectId}/members/{memberId}/roles")
    public ProjectMemberView replaceMemberRoles(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @RequestBody ProjectMemberRolesUpdateRequest request
    ) {
        return projectManagementService.replaceMemberRoles(tenantId, projectId, memberId, request);
    }

    @PostMapping("/{projectId}/members/{memberId}/remove")
    public ProjectMemberView removeMember(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @PathVariable UUID memberId
    ) {
        return projectManagementService.removeMember(tenantId, projectId, memberId);
    }

    @GetMapping("/{projectId}/roles")
    public List<ProjectRoleView> listRoles(@PathVariable UUID tenantId, @PathVariable UUID projectId) {
        return projectManagementService.listRoles(tenantId, projectId);
    }

    @PostMapping("/{projectId}/roles")
    public ProjectRoleView createRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectRoleCreateRequest request
    ) {
        return projectManagementService.createRole(tenantId, projectId, request);
    }

    @PutMapping("/{projectId}/roles/{roleId}")
    public ProjectRoleView updateRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @Valid @RequestBody ProjectRoleUpdateRequest request
    ) {
        return projectManagementService.updateRole(tenantId, projectId, roleId, request);
    }

    @DeleteMapping("/{projectId}/roles/{roleId}")
    public void deleteRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @PathVariable UUID roleId
    ) {
        projectManagementService.deleteRole(tenantId, projectId, roleId);
    }
}
