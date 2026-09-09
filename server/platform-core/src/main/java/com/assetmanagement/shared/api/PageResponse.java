package com.assetmanagement.shared.api;

import java.util.List;

public record PageResponse<T>(
        List<T> records,
        long current,
        long size,
        long total
) {
    public static <T> PageResponse<T> empty(long current, long size) {
        return new PageResponse<>(List.of(), current, size, 0);
    }
}
