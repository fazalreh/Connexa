package com.connexa.api.domain.event;

import java.util.List;
import java.util.Objects;

public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public PageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items is required"));
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than zero");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be zero or greater");
        }
    }

    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0L);
    }
}
