package com.assetmanagement.iam.domain;

import com.assetmanagement.project.domain.Project;
import com.assetmanagement.shared.domain.BaseEntity;
import com.assetmanagement.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private RoleScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(name = "protected_role", nullable = false)
    private boolean protectedRole;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new LinkedHashSet<>();

    protected Role() {
    }

    public Role(String code, String name, RoleScope scope, Project project) {
        this(code, name, scope, project == null ? null : project.getTenant(), project);
    }

    public Role(String code, String name, RoleScope scope, Tenant tenant, Project project) {
        this.code = code;
        this.name = name;
        this.scope = scope;
        this.tenant = tenant;
        this.project = project;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void markSystemProtected() {
        this.systemRole = true;
        this.protectedRole = true;
    }

    public void replacePermissions(Set<Permission> next) {
        permissions.clear();
        if (next != null) {
            permissions.addAll(next);
        }
    }

    /**
     * 增量同步权限，避免每次 clear+全量重写导致对 {@code role_permissions} 的大规模 DELETE。
     */
    public void syncPermissions(Set<Permission> desired) {
        Set<Permission> next = desired == null ? Set.of() : desired;
        Set<java.util.UUID> desiredIds = next.stream()
                .map(Permission::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        permissions.removeIf(permission -> !desiredIds.contains(permission.getId()));
        Set<java.util.UUID> existingIds = permissions.stream()
                .map(Permission::getId)
                .collect(Collectors.toSet());
        for (Permission permission : next) {
            if (!existingIds.contains(permission.getId())) {
                permissions.add(permission);
            }
        }
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public RoleScope getScope() {
        return scope;
    }

    public Project getProject() {
        return project;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public boolean isProtectedRole() {
        return protectedRole;
    }

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }
}
