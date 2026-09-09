package com.assetmanagement.mqtt.application;

import com.assetmanagement.mqtt.api.MqttSecretField;
import com.assetmanagement.mqtt.api.MqttSecretPatch;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.shared.security.SecretCipher;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Objects;

@Service
public class MqttSecretService {

    private final SecretCipher cipher;

    public MqttSecretService(SecretCipher cipher) {
        this.cipher = cipher;
    }

    public void applyPatch(MqttConnection connection, MqttSecretPatch patch) {
        if (patch == null || !containsMutation(patch)) {
            return;
        }
        try (SecretValues values = decrypt(connection)) {
            values.username = update(values.username, patch.username(), patch, MqttSecretField.USERNAME);
            values.password = update(values.password, patch.password(), patch, MqttSecretField.PASSWORD);
            values.caCertificate = update(
                    values.caCertificate,
                    patch.caCertificate(),
                    patch,
                    MqttSecretField.CA_CERTIFICATE
            );
            values.clientCertificate = update(
                    values.clientCertificate,
                    patch.clientCertificate(),
                    patch,
                    MqttSecretField.CLIENT_CERTIFICATE
            );
            values.privateKey = update(
                    values.privateKey,
                    patch.privateKey(),
                    patch,
                    MqttSecretField.PRIVATE_KEY
            );
            validateCombination(values);
            connection.replaceSecrets(
                    encrypt(values.username),
                    encrypt(values.password),
                    encrypt(values.caCertificate),
                    encrypt(values.clientCertificate),
                    encrypt(values.privateKey)
            );
        }
    }

    public SecretValues decrypt(MqttConnection connection) {
        String keyVersion = connection.getSecretKeyVersion();
        String algorithm = connection.getSecretEncryptionAlgorithm();
        SecretValues result = new SecretValues();
        try {
            result.username = decrypt(connection.getUsernameCiphertext(), keyVersion, algorithm);
            result.password = decrypt(connection.getPasswordCiphertext(), keyVersion, algorithm);
            result.caCertificate = decrypt(connection.getCaCertificateCiphertext(), keyVersion, algorithm);
            result.clientCertificate = decrypt(
                    connection.getClientCertificateCiphertext(),
                    keyVersion,
                    algorithm
            );
            result.privateKey = decrypt(connection.getPrivateKeyCiphertext(), keyVersion, algorithm);
            return result;
        } catch (RuntimeException exception) {
            result.close();
            throw exception;
        }
    }

    private char[] decrypt(byte[] ciphertext, String keyVersion, String algorithm) {
        if (ciphertext == null) {
            return null;
        }
        if (keyVersion == null || algorithm == null) {
            Arrays.fill(ciphertext, (byte) 0);
            throw new IllegalStateException("Encrypted MQTT secret is missing envelope metadata");
        }
        try {
            return cipher.decrypt(new SecretCipher.EncryptedSecret(ciphertext, keyVersion, algorithm));
        } finally {
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    private SecretCipher.EncryptedSecret encrypt(char[] value) {
        return value == null ? null : cipher.encrypt(value);
    }

    private static char[] update(
            char[] current,
            String supplied,
            MqttSecretPatch patch,
            MqttSecretField field
    ) {
        boolean clear = patch.clear().contains(field);
        boolean replace = supplied != null && !supplied.isBlank();
        if (clear && replace) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "A secret cannot be replaced and cleared in the same request"
            );
        }
        if (clear) {
            wipe(current);
            return null;
        }
        if (replace) {
            wipe(current);
            return supplied.toCharArray();
        }
        return current;
    }

    private static boolean containsMutation(MqttSecretPatch patch) {
        return !patch.clear().isEmpty()
                || hasText(patch.username())
                || hasText(patch.password())
                || hasText(patch.caCertificate())
                || hasText(patch.clientCertificate())
                || hasText(patch.privateKey());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void validateCombination(SecretValues values) {
        if (values.password != null && values.username == null) {
            invalid("MQTT password requires a username");
        }
        if ((values.clientCertificate == null) != (values.privateKey == null)) {
            invalid("MQTT client certificate and private key must be configured together");
        }
    }

    private static void invalid(String message) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private static void wipe(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    public static final class SecretValues implements AutoCloseable {
        private char[] username;
        private char[] password;
        private char[] caCertificate;
        private char[] clientCertificate;
        private char[] privateKey;

        private SecretValues() {
        }

        public char[] username() {
            return copy(username);
        }

        public char[] password() {
            return copy(password);
        }

        public char[] caCertificate() {
            return copy(caCertificate);
        }

        public char[] clientCertificate() {
            return copy(clientCertificate);
        }

        public char[] privateKey() {
            return copy(privateKey);
        }

        @Override
        public void close() {
            wipe(username);
            wipe(password);
            wipe(caCertificate);
            wipe(clientCertificate);
            wipe(privateKey);
            username = null;
            password = null;
            caCertificate = null;
            clientCertificate = null;
            privateKey = null;
        }

        private static char[] copy(char[] value) {
            return value == null ? null : Arrays.copyOf(value, value.length);
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(this));
        }

        @Override
        public String toString() {
            return "SecretValues[redacted]";
        }
    }
}
