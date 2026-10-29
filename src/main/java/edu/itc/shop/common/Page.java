package edu.itc.shop.common;

import java.util.List;

/** One page of results, as returned by the list endpoints. */
public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> Page<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new Page<>(content, page, size, totalElements, totalPages);
    }
}
