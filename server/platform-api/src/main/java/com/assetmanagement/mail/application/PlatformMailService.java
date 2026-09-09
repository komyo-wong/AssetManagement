package com.assetmanagement.mail.application;

import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.mail.domain.PlatformMailSettings;
import com.assetmanagement.mail.repository.PlatformMailSettingsRepository;
import com.assetmanagement.platform.application.PlatformAuditRecorder;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.shared.security.SecretCipher;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class PlatformMailService {

    private final CurrentUserProvider currentUserProvider;
    private final PlatformMailSettingsRepository settingsRepository;
    private final SecretCipher secretCipher;
    private final PlatformAuditRecorder platformAuditRecorder;

    public PlatformMailService(
            CurrentUserProvider currentUserProvider,
            PlatformMailSettingsRepository settingsRepository,
            SecretCipher secretCipher,
            PlatformAuditRecorder platformAuditRecorder
    ) {
        this.currentUserProvider = currentUserProvider;
        this.settingsRepository = settingsRepository;
        this.secretCipher = secretCipher;
        this.platformAuditRecorder = platformAuditRecorder;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSettings() {
        require(PermissionCodes.PLATFORM_MAIL_READ);
        return toMap(requireSettings());
    }

    @Transactional
    public Map<String, Object> updateSettings(Map<String, Object> body) {
        require(PermissionCodes.PLATFORM_MAIL_MANAGE);
        PlatformMailSettings settings = requireSettings();
        boolean enabled = body.get("enabled") instanceof Boolean b ? b : settings.isEnabled();
        String host = stringOr(body.get("host"), settings.getHost());
        int port = body.get("port") instanceof Number n ? n.intValue() : settings.getPort();
        if (port <= 0 || port > 65535) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "port is invalid");
        }
        String username = stringOr(body.get("username"), settings.getUsername());
        String fromAddress = stringOr(body.get("fromAddress"), settings.getFromAddress());
        String fromName = stringOr(body.get("fromName"), settings.getFromName());
        boolean useSsl = body.get("useSsl") instanceof Boolean b ? b : settings.isUseSsl();
        boolean useStarttls = body.get("useStarttls") instanceof Boolean b ? b : settings.isUseStarttls();

        settings.apply(enabled, host, port, username, fromAddress, fromName, useSsl, useStarttls);

        boolean passwordChanged = false;
        if (body.containsKey("password")) {
            Object raw = body.get("password");
            passwordChanged = true;
            if (raw == null || String.valueOf(raw).isBlank()) {
                settings.clearPassword();
            } else {
                char[] chars = String.valueOf(raw).toCharArray();
                try {
                    settings.replacePassword(secretCipher.encrypt(chars));
                } finally {
                    Arrays.fill(chars, '\0');
                }
            }
        }

        if (enabled) {
            validateReady(settings);
        }
        PlatformMailSettings saved = settingsRepository.save(settings);
        platformAuditRecorder.record(
                "platform.mail.update",
                "platform_mail",
                saved.getId() == null ? "mail" : saved.getId().toString(),
                Map.of(
                        "enabled", saved.isEnabled(),
                        "host", saved.getHost() == null ? "" : saved.getHost(),
                        "port", saved.getPort(),
                        "username", saved.getUsername() == null ? "" : saved.getUsername(),
                        "fromAddress", saved.getFromAddress() == null ? "" : saved.getFromAddress(),
                        "passwordChanged", passwordChanged
                )
        );
        return toMap(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> sendTest(Map<String, Object> body) {
        require(PermissionCodes.PLATFORM_MAIL_MANAGE);
        PlatformMailSettings settings = requireSettings();
        validateReady(settings);
        String to = requiredString(body, "to");
        sendEmail(settings, to, "【资产管理平台】SMTP 测试邮件",
                "这是一封测试邮件。若你收到此信，说明平台 SMTP 配置可用。");
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("to", to);
        return result;
    }

    /**
     * Used by notification dispatch and password reset — no permission check (system path).
     * Must not be {@code readOnly}, otherwise it can mark a calling write transaction read-only.
     */
    @Transactional
    public void sendNotificationEmail(String to, String subject, String textBody) {
        PlatformMailSettings settings = settingsRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new IllegalStateException("Platform mail settings missing"));
        validateReady(settings);
        sendEmail(settings, to, subject, textBody);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isMailReady() {
        return settingsRepository.findFirstByOrderByCreatedAtAsc()
                .map(this::isReady)
                .orElse(false);
    }

    private void sendEmail(PlatformMailSettings settings, String to, String subject, String textBody) {
        char[] password = decryptPassword(settings);
        try {
            JavaMailSender sender = buildSender(settings, password);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String from = settings.getFromAddress() != null ? settings.getFromAddress() : settings.getUsername();
            if (settings.getFromName() != null && !settings.getFromName().isBlank()) {
                helper.setFrom(new InternetAddress(from, settings.getFromName(), StandardCharsets.UTF_8.name()));
            } else {
                helper.setFrom(from);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, false);
            sender.send(message);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to send mail: " + ex.getMessage());
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private JavaMailSender buildSender(PlatformMailSettings settings, char[] password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getHost());
        sender.setPort(settings.getPort());
        if (settings.getUsername() != null) {
            sender.setUsername(settings.getUsername());
        }
        if (password != null) {
            sender.setPassword(new String(password));
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", settings.getUsername() != null ? "true" : "false");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");
        if (settings.isUseSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", String.valueOf(settings.getPort()));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }
        if (settings.isUseStarttls()) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        return sender;
    }

    private char[] decryptPassword(PlatformMailSettings settings) {
        if (!settings.hasPassword()) {
            return null;
        }
        if (settings.getSecretKeyVersion() == null || settings.getSecretEncryptionAlgorithm() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Mail password envelope is incomplete");
        }
        return secretCipher.decrypt(new SecretCipher.EncryptedSecret(
                settings.getPasswordCiphertext(),
                settings.getSecretKeyVersion(),
                settings.getSecretEncryptionAlgorithm()
        ));
    }

    private void validateReady(PlatformMailSettings settings) {
        if (!settings.isEnabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "SMTP is not enabled");
        }
        if (settings.getHost() == null || settings.getHost().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "SMTP host is required");
        }
        String from = settings.getFromAddress() != null ? settings.getFromAddress() : settings.getUsername();
        if (from == null || !from.contains("@")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "fromAddress (or username) must be a valid email");
        }
    }

    private boolean isReady(PlatformMailSettings settings) {
        try {
            validateReady(settings);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    private PlatformMailSettings requireSettings() {
        return settingsRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Mail settings not found"));
    }

    private void require(String permission) {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        if (!principal.hasPlatformPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Platform permission is required");
        }
    }

    private Map<String, Object> toMap(PlatformMailSettings s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId().toString());
        map.put("enabled", s.isEnabled());
        map.put("host", s.getHost());
        map.put("port", s.getPort());
        map.put("username", s.getUsername());
        map.put("passwordConfigured", s.hasPassword());
        map.put("fromAddress", s.getFromAddress());
        map.put("fromName", s.getFromName());
        map.put("useSsl", s.isUseSsl());
        map.put("useStarttls", s.isUseStarttls());
        map.put("ready", isReady(s));
        map.put("updatedAt", s.getUpdatedAt());
        return map;
    }

    private static String stringOr(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String requiredString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + " is required");
        }
        return String.valueOf(value).trim();
    }
}
