package com.assetmanagement.tenant.domain;

import com.assetmanagement.iam.domain.Role;
import com.assetmanagement.iam.domain.User;
import com.assetmanagement.shared.domain.BaseEntity;
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

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tenant_members")
public class TenantMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private TenantMemberStatus status = TenantMemberStatus.INVITED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private User invitedBy;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tenant_member_roles",
            joinColumns = @JoinColumn(name = "tenant_member_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new LinkedHashSet<>();

    protected TenantMember() {
    }

    public TenantMember(Tenant tenant, User user, User invitedBy) {
        this.tenant = tenant;
        this.user = user;
        this.invitedBy = invitedBy;
        this.invitedAt = Instant.now();
    }

    public void activate(Instant joinedAt) {
        this.status = TenantMemberStatus.ACTIVE;
        this.joinedAt = joinedAt;
    }

    public void replaceRoles(Set<Role> next) {
        roles.clear();
        if (next != null) {
            roles.addAll(next);
        }
    }

    public void remove() {
        this.status = TenantMemberStatus.REMOVED;
    }

    public void suspend() {
        this.status = TenantMemberStatus.SUSPENDED;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public User getUser() {
        return user;
    }

    public TenantMemberStatus getStatus() {
        return status;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
