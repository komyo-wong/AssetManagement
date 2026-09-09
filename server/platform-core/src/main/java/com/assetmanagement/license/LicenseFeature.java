package com.assetmanagement.license;

public final class LicenseFeature {
    public static final String NOTIFY = "notify";
    public static final String LOGIN_COPYRIGHT = "login_copyright";
    public static final String EINK = "eink";
    public static final String BUZZ = "buzz";

    private LicenseFeature() {}

    public static boolean isKnown(String raw) {
        return NOTIFY.equals(raw)
                || LOGIN_COPYRIGHT.equals(raw)
                || EINK.equals(raw)
                || BUZZ.equals(raw);
    }
}
