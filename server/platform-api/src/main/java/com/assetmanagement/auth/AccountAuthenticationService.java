package com.assetmanagement.auth;

import com.assetmanagement.iam.domain.Permission;
import com.assetmanagement.iam.domain.Role;
import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.PermissionRepository;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountAuthenticationService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AccountAuthenticationService(
            UserRepository userRepository,
            PermissionRepository permissionRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 校验账号口令并返回快照。保持只读事务，避免登录时对 {@code iam_users} 行锁/flush
     * 与权限图加载叠在同一写事务里造成提交挂起。
     */
    @Transactional(readOnly = true)
    public AccountSnapshot authenticate(String account, String password) {
        Optional<User> candidate = userRepository.findForAuthentication(account.strip());
        String storedHash = candidate.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches;
        try {
            passwordMatches = passwordEncoder.matches(password, storedHash);
        } catch (RuntimeException malformedStoredHash) {
            passwordMatches = false;
        }
        Instant now = Instant.now();
        if (candidate.isEmpty() || !passwordMatches || !isUsable(candidate.orElseThrow(), now)) {
            throw invalidCredentials();
        }
        return snapshot(candidate.orElseThrow());
    }

    /** 独立短事务更新最近登录时间；失败不影响已签发的会话。 */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordSuccessfulLogin(UUID userId) {
        userRepository.touchLastLoginAt(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public AccountSnapshot loadVerified(UUID userId, long expectedAuthorizationVersion) {
        User user = userRepository.findWithPlatformAuthoritiesById(userId)
                .orElseThrow(AccountAuthenticationService::invalidSession);
        if (!isUsable(user, Instant.now())
                || user.getAuthorizationVersion() != expectedAuthorizationVersion) {
            throw invalidSession();
        }
        return snapshot(user);
    }

    private AccountSnapshot snapshot(User user) {
        Set<String> roleCodes = user.getPlatformRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> permissionCodes = user.getPlatformRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (user.isRootAccount()) {
            roleCodes.add("ROOT");
            permissionRepository.findAll().stream()
                    .map(Permission::getCode)
                    .forEach(permissionCodes::add);
        }
        return new AccountSnapshot(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPreferredLocale(),
                user.isRootAccount(),
                user.getAuthorizationVersion(),
                Set.copyOf(roleCodes),
                Set.copyOf(permissionCodes)
        );
    }

    private static boolean isUsable(User user, Instant now) {
        return user.isActiveAt(now) && (!user.isRootAccount() || user.isProtectedAccount());
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "Account or password is invalid");
    }

    private static BusinessException invalidSession() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication session is invalid");
    }

    public record AccountSnapshot(
            UUID userId,
            String username,
            String email,
            String displayName,
            String preferredLocale,
            boolean rootAccount,
            long authorizationVersion,
            Set<String> platformRoleCodes,
            Set<String> platformPermissionCodes
    ) {
        public CurrentUserPrincipal toPrincipal(UUID sessionId, Set<UUID> tenantIdsClaim) {
            return new CurrentUserPrincipal(
                    userId,
                    sessionId,
                    username,
                    email,
                    displayName,
                    preferredLocale,
                    rootAccount,
                    authorizationVersion,
                    platformRoleCodes,
                    platformPermissionCodes,
                    tenantIdsClaim
            );
        }
    }
}
