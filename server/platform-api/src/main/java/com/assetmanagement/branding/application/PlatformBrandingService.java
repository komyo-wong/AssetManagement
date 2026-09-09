package com.assetmanagement.branding.application;

import com.assetmanagement.branding.domain.PlatformBrandingSettings;
import com.assetmanagement.branding.repository.PlatformBrandingSettingsRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.license.LicenseDefaults;
import com.assetmanagement.license.LicenseFeature;
import com.assetmanagement.license.PlatformLicenseService;
import com.assetmanagement.platform.application.PlatformAuditRecorder;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class PlatformBrandingService {

    /** ~500KB binary as data URL */
    private static final int MAX_BACKGROUND_CHARS = 720_000;
    /** ~50KB binary as data URL */
    private static final int MAX_LOGO_CHARS = 90_000;

    private final PlatformBrandingSettingsRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final PlatformAuditRecorder platformAuditRecorder;
    private final PlatformLicenseService platformLicenseService;

    public PlatformBrandingService(
            PlatformBrandingSettingsRepository repository,
            CurrentUserProvider currentUserProvider,
            PlatformAuditRecorder platformAuditRecorder,
            PlatformLicenseService platformLicenseService
    ) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.platformAuditRecorder = platformAuditRecorder;
        this.platformLicenseService = platformLicenseService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublic() {
        return toMap(requireSettings());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSettings() {
        require(PermissionCodes.PLATFORM_BRANDING_READ);
        return toMap(requireSettings());
    }

    @Transactional
    public Map<String, Object> updateSettings(Map<String, Object> body) {
        require(PermissionCodes.PLATFORM_BRANDING_MANAGE);
        PlatformBrandingSettings settings = requireSettings();
        settings.apply(
                body.containsKey("systemName")
                        ? stringOrNull(body.get("systemName"))
                        : settings.getSystemName(),
                body.containsKey("loginTitle")
                        ? stringOrNull(body.get("loginTitle"))
                        : settings.getLoginTitle(),
                body.containsKey("loginSubtitle")
                        ? stringOrNull(body.get("loginSubtitle"))
                        : settings.getLoginSubtitle(),
                body.containsKey("loginWelcomeTitle")
                        ? stringOrNull(body.get("loginWelcomeTitle"))
                        : settings.getLoginWelcomeTitle(),
                body.containsKey("loginWelcomeSubtitle")
                        ? stringOrNull(body.get("loginWelcomeSubtitle"))
                        : settings.getLoginWelcomeSubtitle()
        );
        if (body.containsKey("loginBackgroundData")) {
            String bg = stringOrNull(body.get("loginBackgroundData"));
            if (bg != null) {
                if (bg.length() > MAX_BACKGROUND_CHARS) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "login background must be <= 500KB");
                }
                if (!isImageDataUrl(bg)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "login background must be an image");
                }
            }
            settings.updateLoginBackgroundData(bg);
        }
        if (body.containsKey("titleLogoData")) {
            String logo = stringOrNull(body.get("titleLogoData"));
            if (logo != null) {
                if (logo.length() > MAX_LOGO_CHARS) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "title logo must be <= 50KB");
                }
                if (!isImageDataUrl(logo)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "title logo must be an image");
                }
            }
            settings.updateTitleLogoData(logo);
        }
        if (body.containsKey("copyrightText")) {
            if (!platformLicenseService.has(LicenseFeature.LOGIN_COPYRIGHT)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "修改登录页版权需要授权");
            }
            settings.updateCopyrightText(stringOrNull(body.get("copyrightText")));
        }
        PlatformBrandingSettings saved = repository.save(settings);
        platformAuditRecorder.record(
                "platform.branding.update",
                "platform_branding",
                saved.getId() == null ? "branding" : saved.getId().toString(),
                Map.of(
                        "systemName", saved.getSystemName() == null ? "" : saved.getSystemName(),
                        "loginTitle", saved.getLoginTitle() == null ? "" : saved.getLoginTitle(),
                        "hasLoginBackground", saved.getLoginBackgroundData() != null,
                        "hasTitleLogo", saved.getTitleLogoData() != null
                )
        );
        return toMap(saved);
    }

    private PlatformBrandingSettings requireSettings() {
        return repository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(() -> repository.save(newPlatformDefaults()));
    }

    private static PlatformBrandingSettings newPlatformDefaults() {
        return PlatformBrandingSettings.bootstrap("资产管理平台");
    }

    private Map<String, Object> toMap(PlatformBrandingSettings settings) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", settings.getId() == null ? null : settings.getId().toString());
        map.put("systemName", settings.getSystemName());
        map.put("loginTitle", settings.getLoginTitle());
        map.put("loginSubtitle", settings.getLoginSubtitle());
        map.put("loginWelcomeTitle", settings.getLoginWelcomeTitle());
        map.put("loginWelcomeSubtitle", settings.getLoginWelcomeSubtitle());
        map.put("loginBackgroundData", settings.getLoginBackgroundData());
        map.put("titleLogoData", settings.getTitleLogoData());
        boolean canEditCopyright = platformLicenseService.has(LicenseFeature.LOGIN_COPYRIGHT);
        map.put("canEditCopyright", canEditCopyright);
        map.put("copyrightText", canEditCopyright && settings.getCopyrightText() != null
                ? settings.getCopyrightText()
                : LicenseDefaults.COPYRIGHT);
        map.put("hasLoginBackground", settings.getLoginBackgroundData() != null);
        map.put("hasTitleLogo", settings.getTitleLogoData() != null);
        map.put("updatedAt", settings.getUpdatedAt() == null ? null : settings.getUpdatedAt().toString());
        return map;
    }

    private static boolean isImageDataUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("data:image/");
    }

    private void require(String permission) {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        if (!principal.hasPlatformPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Missing permission: " + permission);
        }
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
