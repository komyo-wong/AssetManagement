package com.assetmanagement.license;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LicenseCodec {
    public static final String PREFIX = "AM1";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private LicenseCodec() {}

    public static String issue(PrivateKey privateKey, LicenseClaims claims) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("v", 1);
            body.put("id", claims.id().toString());
            body.put("installId", claims.installId().toString());
            if (claims.who() != null && !claims.who().isBlank()) {
                body.put("who", claims.who().trim());
            }
            body.put("features", new ArrayList<>(claims.featureSet()));
            if (claims.until() != null) {
                body.put("until", claims.until().toString());
            }
            if (claims.maxBeacons() != null) {
                body.put("maxBeacons", claims.maxBeacons());
            }
            if (claims.maxGateways() != null) {
                body.put("maxGateways", claims.maxGateways());
            }
            byte[] payload = MAPPER.writeValueAsBytes(body);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(payload);
            return PREFIX + "." + B64.encodeToString(payload) + "." + B64.encodeToString(signer.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to issue license", ex);
        }
    }

    public static LicenseClaims verify(String token) {
        return verify(token, LicensePublicKeys.ed25519());
    }

    public static LicenseClaims verify(String token, PublicKey publicKey) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("license token is blank");
        }
        String raw = token.trim().replaceAll("\\s+", "");
        String[] parts = raw.split("\\.", 3);
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException("license token format is invalid");
        }
        byte[] payload;
        byte[] signature;
        try {
            payload = B64D.decode(parts[1]);
            signature = B64D.decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("license token encoding is invalid");
        }
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(payload);
            if (!verifier.verify(signature)) {
                throw new IllegalArgumentException("license signature is invalid");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("license signature is invalid");
        }
        return parsePayload(payload);
    }

    @SuppressWarnings("unchecked")
    private static LicenseClaims parsePayload(byte[] payload) {
        try {
            Map<String, Object> body = MAPPER.readValue(payload, Map.class);
            UUID id = uuid(body.get("id"), "id");
            UUID installId = uuid(body.get("installId"), "installId");
            String who = body.get("who") == null ? null : String.valueOf(body.get("who")).trim();
            Set<String> features = new LinkedHashSet<>();
            Object rawFeatures = body.get("features");
            if (rawFeatures instanceof List<?> list) {
                for (Object item : list) {
                    if (item == null) {
                        continue;
                    }
                    String feature = String.valueOf(item).trim();
                    if (LicenseFeature.isKnown(feature)) {
                        features.add(feature);
                    }
                }
            }
            LocalDate until = null;
            if (body.get("until") != null && !String.valueOf(body.get("until")).isBlank()) {
                until = LocalDate.parse(String.valueOf(body.get("until")).trim());
            }
            Integer maxBeacons = intOrNull(body.get("maxBeacons"));
            Integer maxGateways = intOrNull(body.get("maxGateways"));
            return new LicenseClaims(id, installId, who, features, until, maxBeacons, maxGateways);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("license payload is invalid");
        }
    }

    private static Integer intOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            int value;
            if (raw instanceof Number number) {
                value = number.intValue();
            } else {
                String text = String.valueOf(raw).trim();
                if (text.isEmpty()) {
                    return null;
                }
                value = Integer.parseInt(text);
            }
            return value < 0 ? null : value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("license quantity limit is invalid");
        }
    }

    private static UUID uuid(Object raw, String field) {
        if (raw == null) {
            throw new IllegalArgumentException("license " + field + " is required");
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("license " + field + " is invalid");
        }
    }

    public static String fingerprint(String token) {
        String raw = token == null ? "" : token.trim().replaceAll("\\s+", "");
        return Integer.toHexString(raw.hashCode());
    }
}
