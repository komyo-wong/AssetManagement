package com.assetmanagement.mqtt.worker.security;

import com.assetmanagement.shared.security.SecretCipher;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AES-256-GCM envelope cipher. The persisted payload contains only a format
 * byte, a random nonce and authenticated ciphertext; key material remains in
 * deployment configuration.
 */
public final class AesGcmSecretCipher implements SecretCipher {

    static final String ALGORITHM = "AES-256-GCM";
    private static final String JCA_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String activeKeyVersion;
    private final Map<String, SecretKey> keys;
    private final SecureRandom secureRandom;

    public AesGcmSecretCipher(
            String activeKeyVersion,
            String activeKeyBase64,
            Map<String, String> decryptionKeysBase64
    ) {
        this(activeKeyVersion, activeKeyBase64, decryptionKeysBase64, new SecureRandom());
    }

    AesGcmSecretCipher(
            String activeKeyVersion,
            String activeKeyBase64,
            Map<String, String> decryptionKeysBase64,
            SecureRandom secureRandom
    ) {
        this.activeKeyVersion = requireText(activeKeyVersion, "active key version");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");

        Map<String, SecretKey> configuredKeys = new LinkedHashMap<>();
        if (decryptionKeysBase64 != null) {
            decryptionKeysBase64.forEach((version, encoded) ->
                    configuredKeys.put(requireText(version, "key version"), decodeKey(encoded)));
        }
        configuredKeys.put(this.activeKeyVersion, decodeKey(activeKeyBase64));
        this.keys = Map.copyOf(configuredKeys);
    }

    @Override
    public EncryptedSecret encrypt(char[] plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] clearBytes = encode(plaintext);
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(JCA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyVersion), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(clearBytes);
            ByteBuffer envelope = ByteBuffer.allocate(1 + nonce.length + encrypted.length);
            envelope.put(FORMAT_VERSION).put(nonce).put(encrypted);
            Arrays.fill(encrypted, (byte) 0);
            return new EncryptedSecret(envelope.array(), activeKeyVersion, ALGORITHM);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret encryption failed", exception);
        } finally {
            Arrays.fill(clearBytes, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
        }
    }

    @Override
    public char[] decrypt(EncryptedSecret encryptedSecret) {
        Objects.requireNonNull(encryptedSecret, "encryptedSecret");
        if (!ALGORITHM.equals(encryptedSecret.algorithm())) {
            throw new IllegalArgumentException("Unsupported secret encryption algorithm");
        }
        SecretKey key = keys.get(encryptedSecret.keyVersion());
        if (key == null) {
            throw new IllegalArgumentException("Secret key version is not configured");
        }

        byte[] envelope = encryptedSecret.ciphertext();
        if (envelope.length <= 1 + NONCE_BYTES || envelope[0] != FORMAT_VERSION) {
            Arrays.fill(envelope, (byte) 0);
            throw new IllegalArgumentException("Invalid encrypted secret envelope");
        }
        byte[] nonce = Arrays.copyOfRange(envelope, 1, 1 + NONCE_BYTES);
        byte[] encrypted = Arrays.copyOfRange(envelope, 1 + NONCE_BYTES, envelope.length);
        byte[] clearBytes = null;
        try {
            Cipher cipher = Cipher.getInstance(JCA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            clearBytes = cipher.doFinal(encrypted);
            return decode(clearBytes);
        } catch (AEADBadTagException exception) {
            throw new IllegalArgumentException("Encrypted secret authentication failed", exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret decryption failed", exception);
        } finally {
            Arrays.fill(envelope, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(encrypted, (byte) 0);
            if (clearBytes != null) {
                Arrays.fill(clearBytes, (byte) 0);
            }
        }
    }

    private static SecretKey decodeKey(String encoded) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(requireText(encoded, "base64 key"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Secret key must be valid Base64", exception);
        }
        try {
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("Secret key must contain exactly 32 bytes");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    private static byte[] encode(char[] value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value));
            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            if (encoded.hasArray()) {
                Arrays.fill(encoded.array(), (byte) 0);
            }
            return result;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Secret contains invalid Unicode", exception);
        }
    }

    private static char[] decode(byte[] value) {
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value));
            char[] result = new char[decoded.remaining()];
            decoded.get(result);
            if (decoded.hasArray()) {
                Arrays.fill(decoded.array(), '\0');
            }
            return result;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Decrypted secret is not valid UTF-8", exception);
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be configured");
        }
        return value.trim();
    }
}
