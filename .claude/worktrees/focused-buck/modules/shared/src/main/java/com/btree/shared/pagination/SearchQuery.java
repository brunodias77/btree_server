package com.btree.shared.pagination;

/**
 * Parâmetros de busca com paginação, ordenação e termo de pesquisa.
 * Usado como input para queries de listagem nos gateways.
 *
 * @param page      página atual (zero-based)
 * @param size      itens por página
 * @param terms     termo de busca livre (pode ser vazio)
 * @param sort      campo de ordenação (ex: "name", "createdAt")
 * @param direction direção da ordenação: "asc" ou "desc"
 */
public record SearchQuery(
        int page,
        int size,
        String terms,
        String sort,
        String direction
) {

    public SearchQuery {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
        terms     = terms     != null ? terms.trim()     : "";
        sort      = sort      != null ? sort.trim()      : "createdAt";
        direction = direction != null ? direction.trim() : "asc";
    }

    public static SearchQuery of(final int page, final int size, final String terms) {
        return new SearchQuery(page, size, terms, "createdAt", "asc");
    }

    public static SearchQuery of(
            final int page,
            final int size,
            final String terms,
            final String sort,
            final String direction
    ) {
        return new SearchQuery(page, size, terms, sort, direction);
    }

    public PageRequest toPageRequest() {
        return PageRequest.of(page, size);
    }

    public boolean hasTerms() {
        return terms != null && !terms.isBlank();
    }

    public boolean isAscending() {
        return "asc".equalsIgnoreCase(direction);
    }
}
