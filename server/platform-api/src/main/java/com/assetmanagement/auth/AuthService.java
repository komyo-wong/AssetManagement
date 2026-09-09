package com.assetmanagement.auth;

import com.assetmanagement.auth.AccountAuthenticationService.AccountSnapshot;
import com.assetmanagement.auth.session.RefreshSessionStore;
import com.assetmanagement.auth.token.AccessTokenService;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final AccountAuthenticationService accountAuthenticationService;
    private final TenantMembershipReader tenantMembershipReader;
    private final RefreshSessionStore refreshSessionStore;
    private final AccessTokenService accessTokenService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuthAuditService authAuditService;

    public AuthService(
            AccountAuthenticationService accountAuthenticationService,
            TenantMembershipReader tenantMembershipReader,
            RefreshSessionStore refreshSessionStore,
            AccessTokenService accessTokenService,
            LoginRateLimiter loginRateLimiter,
            AuthAuditService authAuditService
    ) {
        this.accountAuthenticationService = accountAuthenticationService;
        this.tenantMembershipReader = tenantMembershipReader;
        this.refreshSessionStore = refreshSessionStore;
        this.accessTokenService = accessTokenService;
        this.loginRateLimiter = loginRateLimiter;
        this.authAuditService = authAuditService;
    }

    public AuthResult login(
            String account,
            String password,
            AuthClient client,
            AuthRequestMetadata metadata
    ) {
        AuthClient resolved = client == null ? AuthClient.WEB : client;
        String accountFingerprint = loginRateLimiter.accountFingerprint(account);
        try {
            loginRateLimiter.check(account, metadata.remoteAddress());
            AccountSnapshot accountSnapshot = accountAuthenticationService.authenticate(account, password);
            Set<UUID> tenantIds = tenantMembershipReader.activeTenantIds(accountSnapshot.userId());
            RefreshSessionStore.IssuedRefreshToken refreshToken = refreshSessionStore.create(
                    accountSnapshot.userId(),
                    accountSnapshot.authorizationVersion(),
                    resolved
            );
            try {
                CurrentUserPrincipal principal = accountSnapshot.toPrincipal(
                        refreshToken.session().sessionId(),
                        tenantIds
                );
                AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(
                        principal,
                        refreshToken.session().sessionId(),
                        tenantIds,
                        resolved
                );
                try {
                    authAuditService.loginSucceeded(
                            accountSnapshot.userId(),
                            refreshToken.session().sessionId(),
                            metadata
                    );
                } catch (RuntimeException ignored) {
                    // 审计写入失败不阻断登录
                }
                try {
                    accountAuthenticationService.recordSuccessfulLogin(accountSnapshot.userId());
                } catch (RuntimeException ignored) {
                    // 最近登录时间更新失败不阻断登录
                }
                loginRateLimiter.resetAccount(account);
                return new AuthResult(accessToken, refreshToken);
            } catch (RuntimeException exception) {
                refreshSessionStore.revoke(refreshToken.token());
                throw exception;
            }
        } catch (BusinessException exception) {
            authAuditService.loginFailed(
                    accountFingerprint,
                    exception.getErrorCode().code(),
                    metadata
            );
            throw exception;
        }
    }

    public AuthResult refresh(String presentedRefreshToken, AuthRequestMetadata metadata) {
        RefreshSessionStore.RotationResult rotation = refreshSessionStore.rotate(presentedRefreshToken);
        RefreshSessionStore.IssuedRefreshToken replacement = rotation.issuedToken();
        try {
            AccountSnapshot account = accountAuthenticationService.loadVerified(
                    replacement.session().userId(),
                    replacement.session().authorizationVersion()
            );
            Set<UUID> tenantIds = tenantMembershipReader.activeTenantIds(account.userId());
            CurrentUserPrincipal principal = account.toPrincipal(
                    replacement.session().sessionId(),
                    tenantIds
            );
            AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(
                    principal,
                    replacement.session().sessionId(),
                    tenantIds,
                    replacement.session().client()
            );
            authAuditService.refreshSucceeded(
                    account.userId(),
                    replacement.session().sessionId(),
                    metadata
            );
            return new AuthResult(accessToken, replacement);
        } catch (RuntimeException exception) {
            refreshSessionStore.revoke(replacement.token());
            throw exception;
        }
    }

    public void logout(String presentedRefreshToken, AuthRequestMetadata metadata) {
        Optional<RefreshSessionStore.RefreshSession> revoked =
                refreshSessionStore.revoke(presentedRefreshToken);
        revoked.ifPresent(session -> authAuditService.logoutSucceeded(
                session.userId(),
                session.sessionId(),
                metadata
        ));
    }

    public record AuthResult(
            AccessTokenService.IssuedAccessToken accessToken,
            RefreshSessionStore.IssuedRefreshToken refreshToken
    ) {
    }
}
