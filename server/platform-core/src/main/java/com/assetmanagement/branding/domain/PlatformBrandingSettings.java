package com.assetmanagement.branding.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_branding_settings")
public class PlatformBrandingSettings extends BaseEntity {

    @Column(name = "system_name", length = 120)
    private String systemName;

    @Column(name = "login_title", length = 200)
    private String loginTitle;

    @Column(name = "login_subtitle", length = 500)
    private String loginSubtitle;

    @Column(name = "login_welcome_title", length = 200)
    private String loginWelcomeTitle;

    @Column(name = "login_welcome_subtitle", length = 500)
    private String loginWelcomeSubtitle;

    @Column(name = "login_background_data")
    private String loginBackgroundData;

    @Column(name = "title_logo_data")
    private String titleLogoData;
    @Column(name = "copyright_text", length = 300)
    private String copyrightText;

    protected PlatformBrandingSettings() {
    }

    public static PlatformBrandingSettings bootstrap(String systemName) {
        PlatformBrandingSettings settings = new PlatformBrandingSettings();
        settings.apply(systemName, null, null, null, null);
        return settings;
    }

    public void apply(
            String systemName,
            String loginTitle,
            String loginSubtitle,
            String loginWelcomeTitle,
            String loginWelcomeSubtitle
    ) {
        this.systemName = blankToNull(systemName);
        this.loginTitle = blankToNull(loginTitle);
        this.loginSubtitle = blankToNull(loginSubtitle);
        this.loginWelcomeTitle = blankToNull(loginWelcomeTitle);
        this.loginWelcomeSubtitle = blankToNull(loginWelcomeSubtitle);
    }

    public void updateLoginBackgroundData(String loginBackgroundData) {
        this.loginBackgroundData = blankToNull(loginBackgroundData);
    }

    public void updateTitleLogoData(String titleLogoData) {
        this.titleLogoData = blankToNull(titleLogoData);
    }

    public void updateCopyrightText(String copyrightText) {
        this.copyrightText = blankToNull(copyrightText);
    }

    public String getSystemName() {
        return systemName;
    }

    public String getLoginTitle() {
        return loginTitle;
    }

    public String getLoginSubtitle() {
        return loginSubtitle;
    }

    public String getLoginWelcomeTitle() {
        return loginWelcomeTitle;
    }

    public String getLoginWelcomeSubtitle() {
        return loginWelcomeSubtitle;
    }

    public String getLoginBackgroundData() {
        return loginBackgroundData;
    }

    public String getTitleLogoData() {
        return titleLogoData;
    }

    public String getCopyrightText() {
        return copyrightText;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
