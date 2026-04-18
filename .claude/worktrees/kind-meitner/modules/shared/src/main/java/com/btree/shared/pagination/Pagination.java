package com.btree.shared.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * Resultado paginado — usado em toda a cadeia: gateway → use case → controller.
 *
 * @param <T> tipo do item paginado
 */
public record Pagination<T>(
        List<T> items,
        int currentPage,
        int perPage,
        long total
) {

    public static <T> Pagination<T> of(
            final List<T> items,
            final int currentPage,
            final int perPage,
            final long total
    ) {
        return new Pagination<>(items, currentPage, perPage, total);
    }

    public static <T> Pagination<T> empty(final PageRequest request) {
        return new Pagination<>(List.of(), request.page(), request.size(), 0L);
    }

    public <R> Pagination<R> map(final Function<T, R> mapper) {
        return new Pagination<>(
                items.stream().map(mapper).toList(),
                currentPage,
                perPage,
                total
        );
    }

    public int totalPages() {
        if (total == 0) return 0;
        return (int) Math.ceil((double) total / perPage);
    }

    public boolean hasNext() {
        return currentPage < totalPages() - 1;
    }

    public boolean hasPrevious() {
        return currentPage > 0;
    }
}
