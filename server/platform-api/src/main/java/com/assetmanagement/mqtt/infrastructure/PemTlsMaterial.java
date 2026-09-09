package com.assetmanagement.mqtt.infrastructure;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

final class PemTlsMaterial {

    private PemTlsMaterial() {
    }

    static TrustManagerFactory trustManager(char[] caPem) throws GeneralSecurityException {
        byte[] pem = encode(caPem);
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(
                    new ByteArrayInputStream(pem)
            );
            if (certificates.isEmpty()) {
                throw new GeneralSecurityException("No CA certificate was found");
            }
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            int index = 0;
            for (Certificate certificate : certificates) {
                trustStore.setCertificateEntry("ca-" + index++, certificate);
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
            );
            factory.init(trustStore);
            return factory;
        } catch (GeneralSecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GeneralSecurityException("CA certificate could not be loaded", exception);
        } finally {
            Arrays.fill(pem, (byte) 0);
        }
    }

    static KeyManagerFactory keyManager(char[] certificatePem, char[] privateKeyPem)
            throws GeneralSecurityException {
        byte[] certificates = encode(certificatePem);
        byte[] privateKey = encode(privateKeyPem);
        char[] temporaryPassword = Long.toHexString(System.nanoTime()).toCharArray();
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> parsedCertificates = certificateFactory.generateCertificates(
                    new ByteArrayInputStream(certificates)
            );
            if (parsedCertificates.isEmpty()) {
                throw new GeneralSecurityException("No client certificate was found");
            }
            PrivateKey parsedKey = parsePrivateKey(privateKey);
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry(
                    "mqtt-client",
                    parsedKey,
                    temporaryPassword,
                    parsedCertificates.toArray(Certificate[]::new)
            );
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore, temporaryPassword);
            return factory;
        } catch (GeneralSecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GeneralSecurityException("Client key material could not be loaded", exception);
        } finally {
            Arrays.fill(certificates, (byte) 0);
            Arrays.fill(privateKey, (byte) 0);
            Arrays.fill(temporaryPassword, '\0');
        }
    }

    private static PrivateKey parsePrivateKey(byte[] pemBytes) throws GeneralSecurityException {
        String pem = new String(pemBytes, StandardCharsets.US_ASCII);
        if (!pem.contains("-----BEGIN PRIVATE KEY-----")) {
            throw new GeneralSecurityException("Private key must use unencrypted PKCS#8 PEM format");
        }
        String encoded = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new GeneralSecurityException("Private key PEM is invalid", exception);
        }
        try {
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            List<GeneralSecurityException> failures = new ArrayList<>();
            for (String algorithm : List.of("RSA", "EC", "Ed25519", "Ed448")) {
                try {
                    return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
                } catch (GeneralSecurityException exception) {
                    failures.add(exception);
                }
            }
            GeneralSecurityException failure = new GeneralSecurityException(
                    "Private key algorithm is not supported"
            );
            failures.forEach(failure::addSuppressed);
            throw failure;
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    private static byte[] encode(char[] value) {
        if (value == null) {
            return new byte[0];
        }
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            if (encoded.hasArray()) {
                Arrays.fill(encoded.array(), (byte) 0);
            }
            return bytes;
        } catch (Exception exception) {
            throw new IllegalArgumentException("PEM material contains invalid Unicode", exception);
        }
    }
}
