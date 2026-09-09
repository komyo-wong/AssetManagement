package com.assetmanagement.geotag;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Local device name ∩ GeoTag {@code getList} SN. */
public final class GeotagDeviceMatcher {

    private GeotagDeviceMatcher() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean namesMatch(String localDeviceName, String cloudSn) {
        String left = normalize(localDeviceName);
        String right = normalize(cloudSn);
        return !left.isEmpty() && left.equals(right);
    }

    public static Set<String> intersection(Collection<String> localDeviceNames, Collection<String> cloudSns) {
        Set<String> remote = new LinkedHashSet<>();
        if (cloudSns != null) {
            for (String sn : cloudSns) {
                String key = normalize(sn);
                if (!key.isEmpty()) {
                    remote.add(key);
                }
            }
        }
        Set<String> matched = new LinkedHashSet<>();
        if (localDeviceNames == null) {
            return matched;
        }
        for (String name : localDeviceNames) {
            String key = normalize(name);
            if (!key.isEmpty() && remote.contains(key)) {
                matched.add(key);
            }
        }
        return matched;
    }
}
