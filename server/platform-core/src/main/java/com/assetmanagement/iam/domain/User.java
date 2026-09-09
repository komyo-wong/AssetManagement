package com.assetmanagement.iam.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity(name = "IamUser")
@Table(name = "iam_users")
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, length = 80)
    private String username;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "ui_preferences_json")
    private String uiPreferencesJson;

    @Column(name = "alert_sound_data")
    private String alertSoundData;

    @Column(name = "alert_sound_name", length = 160)
    private String alertSoundName;

    @Column(name = "preferred_locale", nullable = false, length = 16)
    private String preferredLocale = "zh-CN";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private UserStatus status = UserStatus.PENDING;

    @Column(name = "root_account", nullable = false)
    private boolean rootAccount;

    @Column(name = "protected_account", nullable = false)
    private boolean protectedAccount;

    @Column(name = "activated_until")
    private Instant activatedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "authorization_version", nullable = false)
    private long authorizationVersion;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_platform_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> platformRoles = new LinkedHashSet<>();

    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /** Offline bootstrap helper for the single protected Root account. */
    public static User bootstrapRoot(String username, String email, String passwordHash, String displayName) {
        User user = new User(username, email, passwordHash);
        user.displayName = displayName;
        user.status = UserStatus.ACTIVE;
        user.rootAccount = true;
        user.protectedAccount = true;
        return user;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getUiPreferencesJson() {
        return uiPreferencesJson;
    }

    public String getAlertSoundData() {
        return alertSoundData;
    }

    public String getAlertSoundName() {
        return alertSoundName;
    }

    public String getPreferredLocale() {
        return preferredLocale;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isRootAccount() {
        return rootAccount;
    }

    public boolean isProtectedAccount() {
        return protectedAccount;
    }

    public Instant getActivatedUntil() {
        return activatedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public long getAuthorizationVersion() {
        return authorizationVersion;
    }

    public boolean isActiveAt(Instant instant) {
        return status == UserStatus.ACTIVE
                && (activatedUntil == null || activatedUntil.isAfter(instant));
    }

    public void recordSuccessfulLogin(Instant instant) {
        lastLoginAt = instant;
    }

    public void replacePasswordHash(String newPasswordHash) {
        passwordHash = newPasswordHash;
        authorizationVersion++;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void suspend() {
        if (rootAccount || protectedAccount) {
            throw new IllegalStateException("Protected accounts cannot be suspended");
        }
        this.status = UserStatus.SUSPENDED;
        authorizationVersion++;
    }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void updateEmail(String email) {
        this.email = email;
    }

    public void updateAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void updateUiPreferencesJson(String uiPreferencesJson) {
        this.uiPreferencesJson = uiPreferencesJson;
    }

    public void updateAlertSound(String alertSoundData, String alertSoundName) {
        this.alertSoundData = alertSoundData;
        this.alertSoundName = alertSoundName;
    }

    public void replacePlatformRoles(Set<Role> next) {
        if (rootAccount) {
            throw new IllegalStateException("Root account platform roles cannot be replaced");
        }
        platformRoles.clear();
        if (next != null) {
            platformRoles.addAll(next);
        }
        authorizationVersion++;
    }

    public Set<Role> getPlatformRoles() {
        return Collections.unmodifiableSet(platformRoles);
    }
}
