package com.assetmanagement.geotag;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * GeoTag cloud envelope: AES-128-ECB + RSA/ECB/PKCS1Padding + SHA256withRSA.
 * Keys are Java {@code getEncoded()} Base64 without PEM headers.
 */
public final class GeotagCrypto {

    private GeotagCrypto() {
    }

    public record RsaKeyPair(String publicKey, String privateKey) {
    }

    public static RsaKeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RsaKeyPair(
                    Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate GeoTag RSA key pair", exception);
        }
    }

    public static String encryptEnvelope(String plaintext, String publicKeyBase64) {
        try {
            SecretKey aesKey = generateAesKey();
            String encryptedData = aesEncrypt(plaintext == null ? "{}" : plaintext, aesKey);
            String encryptedAesKey = rsaEncryptAesKey(aesKey, publicKeyBase64);
            return encryptedData + "," + encryptedAesKey;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to encrypt GeoTag envelope", exception);
        }
    }

    public static String decryptEnvelope(String data, String privateKeyBase64) {
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("GeoTag data is empty");
        }
        int comma = data.indexOf(',');
        if (comma <= 0 || comma == data.length() - 1) {
            throw new IllegalArgumentException("GeoTag data format is invalid");
        }
        try {
            String encryptedData = data.substring(0, comma);
            String encryptedAesKey = data.substring(comma + 1);
            PrivateKey privateKey = privateKey(privateKeyBase64);
            SecretKey aesKey = decryptAesKey(encryptedAesKey, privateKey);
            return aesDecrypt(encryptedData, aesKey);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to decrypt GeoTag envelope", exception);
        }
    }

    public static String sign(String data, String privateKeyBase64) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey(privateKeyBase64));
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to sign GeoTag payload", exception);
        }
    }

    public static boolean verify(String data, String signatureBase64, String publicKeyBase64) {
        if (data == null || signatureBase64 == null || publicKeyBase64 == null) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey(publicKeyBase64));
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = Base64.getDecoder().decode(signatureBase64);
            return signature.verify(signBytes);
        } catch (Exception exception) {
            return false;
        }
    }

    public static String signPayload(String encryptedData, long timestamp, String privateKeyBase64) {
        return sign(encryptedData + timestamp, privateKeyBase64);
    }

    public static boolean verifyPayload(String encryptedData, long timestamp, String sign, String publicKeyBase64) {
        return verify(encryptedData + timestamp, sign, publicKeyBase64);
    }

    private static SecretKey generateAesKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }

    private static String aesEncrypt(String plaintext, SecretKey aesKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    private static String aesDecrypt(String base64Data, SecretKey aesKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey);
        return new String(cipher.doFinal(Base64.getDecoder().decode(base64Data)), StandardCharsets.UTF_8);
    }

    private static String rsaEncryptAesKey(SecretKey aesKey, String publicKeyBase64) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey(publicKeyBase64));
        return Base64.getEncoder().encodeToString(cipher.doFinal(aesKey.getEncoded()));
    }

    private static SecretKey decryptAesKey(String base64AesKey, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(base64AesKey));
        return new SecretKeySpec(decrypted, "AES");
    }

    private static PrivateKey privateKey(String privateKeyStr) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(stripKey(privateKeyStr));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private static PublicKey publicKey(String publicKeyStr) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(stripKey(publicKeyStr));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private static String stripKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
    }
}
