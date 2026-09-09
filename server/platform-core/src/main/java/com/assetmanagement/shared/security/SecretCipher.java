package com.assetmanagement.shared.security;

import java.util.Arrays;

/**
 * Port for envelope encryption supplied by deployment infrastructure. No
 * plaintext or master keys are persisted by the domain model.
 */
public interface SecretCipher {

    EncryptedSecret encrypt(char[] plaintext);

    char[] decrypt(EncryptedSecret encryptedSecret);

    record EncryptedSecret(byte[] ciphertext, String keyVersion, String algorithm) {
        public EncryptedSecret {
            ciphertext = ciphertext == null ? new byte[0] : Arrays.copyOf(ciphertext, ciphertext.length);
        }

        @Override
        public byte[] ciphertext() {
            return Arrays.copyOf(ciphertext, ciphertext.length);
        }
    }
}

