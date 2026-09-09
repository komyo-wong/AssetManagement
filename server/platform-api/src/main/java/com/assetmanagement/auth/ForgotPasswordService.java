package com.assetmanagement.auth;

import com.assetmanagement.branding.domain.PlatformBrandingSettings;
import com.assetmanagement.branding.repository.PlatformBrandingSettingsRepository;
import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.mail.application.PlatformMailService;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ForgotPasswordService {

    private static final String DEFAULT_SYSTEM_NAME = "资产管理平台";
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformMailService platformMailService;
    private final PlatformBrandingSettingsRepository brandingSettingsRepository;
    private final LoginRateLimiter loginRateLimiter;
    private final AuthAuditService authAuditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ForgotPasswordService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PlatformMailService platformMailService,
            PlatformBrandingSettingsRepository brandingSettingsRepository,
            LoginRateLimiter loginRateLimiter,
            AuthAuditService authAuditService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.platformMailService = platformMailService;
        this.brandingSettingsRepository = brandingSettingsRepository;
        this.loginRateLimiter = loginRateLimiter;
        this.authAuditService = authAuditService;
    }

    @Transactional
    public void resetAndEmail(String account, AuthRequestMetadata metadata) {
        loginRateLimiter.checkForgot(account, metadata.remoteAddress());
        if (!platformMailService.isMailReady()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "平台未启用邮件服务，无法重置密码");
        }

        String fingerprint = loginRateLimiter.accountFingerprint(account);
        Optional<User> candidate = userRepository.findForAuthentication(account.strip());
        if (candidate.isEmpty() || !candidate.get().isActiveAt(Instant.now())) {
            afterCommit(() -> authAuditService.forgotPasswordSkipped(
                    fingerprint,
                    "not_found_or_inactive",
                    metadata
            ));
            return;
        }

        User user = candidate.get();
        String plainPassword = generatePassword();
        user.replacePasswordHash(passwordEncoder.encode(plainPassword));
        userRepository.save(user);

        String systemName = brandingSettingsRepository.findFirstByOrderByCreatedAtAsc()
                .map(PlatformBrandingSettings::getSystemName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(DEFAULT_SYSTEM_NAME);
        try {
            platformMailService.sendNotificationEmail(
                    user.getEmail(),
                    "【" + systemName + "】密码已重置",
                    buildBody(systemName, user.getUsername(), plainPassword)
            );
        } catch (BusinessException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邮件发送失败，密码未更改，请稍后重试");
        }
        UUID userId = user.getId();
        afterCommit(() -> authAuditService.forgotPasswordIssued(userId, metadata));
    }

    private static void afterCommit(Runnable work) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    work.run();
                } catch (RuntimeException ignored) {
                    // 审计失败不影响重置结果
                }
            }
        });
    }

    private static String buildBody(String systemName, String username, String password) {
        return """
                您好，

                您的账号 %s 已重置密码。

                新密码：%s

                请登录后及时修改密码。如非本人操作，请立即联系管理员。

                %s
                """.formatted(username, password, systemName);
    }

    private String generatePassword() {
        int letterCount = PASSWORD_ALPHABET.length() - 8;
        char[] chars = new char[PASSWORD_LENGTH];
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            chars[i] = PASSWORD_ALPHABET.charAt(secureRandom.nextInt(PASSWORD_ALPHABET.length()));
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char ch : chars) {
            if (Character.isLetter(ch)) {
                hasLetter = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }
        if (!hasLetter) {
            chars[0] = PASSWORD_ALPHABET.charAt(secureRandom.nextInt(letterCount));
        }
        if (!hasDigit) {
            chars[PASSWORD_LENGTH - 1] = PASSWORD_ALPHABET.charAt(
                    letterCount + secureRandom.nextInt(PASSWORD_ALPHABET.length() - letterCount)
            );
        }
        return new String(chars);
    }
}
