package com.assetmanagement.platform.api;

import com.assetmanagement.platform.application.PlatformAdminService;
import com.assetmanagement.mail.application.PlatformMailService;
import com.assetmanagement.branding.application.PlatformBrandingService;
import com.assetmanagement.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {

    private final PlatformAdminService platformAdminService;
    private final PlatformMailService platformMailService;
    private final PlatformBrandingService platformBrandingService;

    public PlatformController(
            PlatformAdminService platformAdminService,
            PlatformMailService platformMailService,
            PlatformBrandingService platformBrandingService
    ) {
        this.platformAdminService = platformAdminService;
        this.platformMailService = platformMailService;
        this.platformBrandingService = platformBrandingService;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return platformAdminService.listUsers();
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@Valid @RequestBody PlatformAdminRequests.CreateUserRequest request) {
        return platformAdminService.createUser(request);
    }

    @PutMapping("/users/{userId}")
    public Map<String, Object> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody PlatformAdminRequests.UpdateUserRequest request
    ) {
        return platformAdminService.updateUser(userId, request);
    }

    @PutMapping("/users/{userId}/password")
    public Map<String, Object> resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody PlatformAdminRequests.ResetPasswordRequest request
    ) {
        return platformAdminService.resetPassword(userId, request);
    }

    @PostMapping("/users/{userId}/activate")
    public Map<String, Object> activateUser(@PathVariable UUID userId) {
        return platformAdminService.activateUser(userId);
    }

    @PostMapping("/users/{userId}/suspend")
    public Map<String, Object> suspendUser(@PathVariable UUID userId) {
        return platformAdminService.suspendUser(userId);
    }

    @PutMapping("/users/{userId}/roles")
    public Map<String, Object> replaceUserRoles(
            @PathVariable UUID userId,
            @RequestBody PlatformAdminRequests.ReplaceUserRolesRequest request
    ) {
        return platformAdminService.replaceUserRoles(userId, request);
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        return platformAdminService.listRoles();
    }

    @PostMapping("/roles")
    public Map<String, Object> createRole(@Valid @RequestBody PlatformAdminRequests.CreateRoleRequest request) {
        return platformAdminService.createRole(request);
    }

    @PutMapping("/roles/{roleId}")
    public Map<String, Object> updateRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody PlatformAdminRequests.UpdateRoleRequest request
    ) {
        return platformAdminService.updateRole(roleId, request);
    }

    @DeleteMapping("/roles/{roleId}")
    public void deleteRole(@PathVariable UUID roleId) {
        platformAdminService.deleteRole(roleId);
    }

    @GetMapping("/permissions")
    public List<Map<String, Object>> permissions() {
        return platformAdminService.listPermissions();
    }

    @GetMapping("/audit")
    public PageResponse<Map<String, Object>> audit(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @RequestParam(required = false) String actor,
            @RequestParam(defaultValue = "false") boolean includeSessionRefresh
    ) {
        return platformAdminService.listAudit(current, size, actor, includeSessionRefresh);
    }

    @GetMapping("/gateways")
    public List<Map<String, Object>> gateways() {
        return platformAdminService.listGateways();
    }

    @GetMapping("/mail-settings")
    public Map<String, Object> mailSettings() {
        return platformMailService.getSettings();
    }

    @PutMapping("/mail-settings")
    public Map<String, Object> updateMailSettings(@RequestBody Map<String, Object> body) {
        return platformMailService.updateSettings(body);
    }

    @PostMapping("/mail-settings/test")
    public Map<String, Object> testMail(@RequestBody Map<String, Object> body) {
        return platformMailService.sendTest(body);
    }

    @GetMapping("/branding-settings")
    public Map<String, Object> brandingSettings() {
        return platformBrandingService.getSettings();
    }

    @PutMapping("/branding-settings")
    public Map<String, Object> updateBrandingSettings(@RequestBody Map<String, Object> body) {
        return platformBrandingService.updateSettings(body);
    }
}
