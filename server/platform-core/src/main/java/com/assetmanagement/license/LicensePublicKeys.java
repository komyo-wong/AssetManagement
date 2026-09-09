package com.assetmanagement.license;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Embedded Ed25519 public key. The matching private key is not in the repository. */
public final class LicensePublicKeys {
    static final String ED25519_X509_B64 = "MCowBQYDK2VwAyEAKChFkOgT//iC9RnHdwDAXlahiO0xyW3zT/WXHdwhnNs=";

    private LicensePublicKeys() {}

    public static PublicKey ed25519() {
        try {
            byte[] der = Base64.getDecoder().decode(ED25519_X509_B64);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("embedded license public key is invalid", ex);
        }
    }
}
