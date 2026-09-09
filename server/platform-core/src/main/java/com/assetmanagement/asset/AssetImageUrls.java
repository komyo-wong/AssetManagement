package com.assetmanagement.asset;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Built-in catalog ids and uploaded document URLs stored on {@code assets.image_url}. */
public final class AssetImageUrls {

    public static final int MAX_BYTES = 100 * 1024;

    public static final Set<String> BUILTIN_IDS = Set.of(
            "laptop", "desktop", "phone", "tablet", "monitor", "tv", "printer", "scanner",
            "projector", "server", "router", "ups", "camera", "cctv", "radio", "headset",
            "speaker", "watch", "pos", "barcode",
            "badge", "keys", "helmet", "vest", "boots", "extinguisher", "firstaid",
            "package", "pallet", "crate", "barrel", "container", "cart", "palletjack", "forklift",
            "car", "van", "truck", "bicycle", "motorcycle", "excavator",
            "toolbox", "drill", "ladder", "generator", "battery", "sensor", "antenna",
            "chair", "desk", "cabinet", "fridge", "ac", "wheelchair", "stretcher", "generic"
    );

    private static final Pattern BUILTIN = Pattern.compile("(?i)^builtin:([a-z0-9-]+)$");
    private static final Pattern PUBLIC_SVG = Pattern.compile("(?i)^/asset-photos/([a-z0-9-]+)\\.svg$");
    private static final Pattern DOCUMENT = Pattern.compile(
            "(?i)^/api/v1/tenants/([0-9a-f-]{36})/projects/([0-9a-f-]{36})/documents/([0-9a-f-]{36})/content$"
    );

    private AssetImageUrls() {
    }

    public static String builtin(String id) {
        return "builtin:" + id.toLowerCase(Locale.ROOT);
    }

    /**
     * Blank → empty (clear). Valid catalog / same-project document URL → normalized value.
     * Anything else → empty optional (caller should reject).
     */
    public static Optional<String> parse(String raw, UUID tenantId, UUID projectId) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return Optional.empty();
        }
        Matcher builtin = BUILTIN.matcher(value);
        if (builtin.matches()) {
            String id = builtin.group(1).toLowerCase(Locale.ROOT);
            return BUILTIN_IDS.contains(id) ? Optional.of(builtin(id)) : Optional.empty();
        }
        Matcher publicSvg = PUBLIC_SVG.matcher(value);
        if (publicSvg.matches()) {
            String id = publicSvg.group(1).toLowerCase(Locale.ROOT);
            return BUILTIN_IDS.contains(id) ? Optional.of(builtin(id)) : Optional.empty();
        }
        Matcher document = DOCUMENT.matcher(value);
        if (document.matches() && tenantId != null && projectId != null) {
            if (tenantId.toString().equalsIgnoreCase(document.group(1))
                    && projectId.toString().equalsIgnoreCase(document.group(2))) {
                return Optional.of("/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/documents/" + document.group(3).toLowerCase(Locale.ROOT) + "/content");
            }
        }
        return Optional.empty();
    }

    public static boolean isBuiltin(String value) {
        return value != null && BUILTIN.matcher(value.trim()).matches();
    }
}
