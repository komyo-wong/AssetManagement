package com.assetmanagement.mqtt.infrastructure;

import com.assetmanagement.shared.security.SecretCipher;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTripsWithoutPersistingPlaintextAndUsesRandomNonces() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher("v1", KEY, Map.of());
        char[] plaintext = "not-a-real-password".toCharArray();

        SecretCipher.EncryptedSecret first = cipher.encrypt(plaintext);
        SecretCipher.EncryptedSecret second = cipher.encrypt(plaintext);

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(new String(first.ciphertext(), StandardCharsets.UTF_8))
                .doesNotContain("not-a-real-password");
        assertThat(cipher.decrypt(first)).isEqualTo(plaintext);
    }

    @Test
    void rejectsCiphertextThatWasModified() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher("v1", KEY, Map.of());
        SecretCipher.EncryptedSecret encrypted = cipher.encrypt("secret".toCharArray());
        byte[] modified = encrypted.ciphertext();
        modified[modified.length - 1] ^= 1;

        var tampered = new SecretCipher.EncryptedSecret(modified, "v1", AesGcmSecretCipher.ALGORITHM);
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Encrypted secret authentication failed");
    }

    @Test
    void canReadAnExplicitPreviousKeyVersion() {
        String previousKey = Base64.getEncoder().encodeToString(new byte[32]);
        byte[] newKeyBytes = new byte[32];
        newKeyBytes[0] = 42;
        String activeKey = Base64.getEncoder().encodeToString(newKeyBytes);
        var oldCipher = new AesGcmSecretCipher("old", previousKey, Map.of());
        var rotatedCipher = new AesGcmSecretCipher("new", activeKey, Map.of("old", previousKey));

        assertThat(rotatedCipher.decrypt(oldCipher.encrypt("rotatable".toCharArray())))
                .isEqualTo("rotatable".toCharArray());
    }
}
