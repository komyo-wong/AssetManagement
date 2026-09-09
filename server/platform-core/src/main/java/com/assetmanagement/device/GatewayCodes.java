package com.assetmanagement.device;

import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 4-digit gateway business codes that stay unique within a project. */
public final class GatewayCodes {

    private static final Pattern NUMERIC = Pattern.compile("^\\d{1,4}$");

    private GatewayCodes() {
    }

    public static String nextNumeric(Collection<String> existingCodes) {
        Set<Integer> used = new HashSet<>();
        Set<String> exact = new HashSet<>();
        if (existingCodes != null) {
            for (String raw : existingCodes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String code = raw.trim().toLowerCase(Locale.ROOT);
                exact.add(code);
                if (NUMERIC.matcher(code).matches()) {
                    used.add(Integer.parseInt(code));
                }
            }
        }
        for (int i = 1; i <= 9999; i++) {
            String padded = String.format("%04d", i);
            if (!used.contains(i) && !exact.contains(padded)) {
                return padded;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法生成唯一网关编码，请稍后重试");
    }
}
