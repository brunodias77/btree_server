package com.btree.application.usecase.catalog.category.list_all_categories;

import com.btree.domain.catalog.entity.Category;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ListAllCategoriesOutput(
        String                     id,
        String                     parentId,
        String                     name,
        String                     slug,
        String                     description,
        String                     imageUrl,
        int                        sortOrder,
        boolean                    active,
        Instant                    createdAt,
        Instant updatedAt,
        List<ListAllCategoriesOutput> children
) {
    /**
     * Monta a árvore completa a partir de uma lista plana de categorias ativas.
     *
     * <p>Algoritmo O(n): agrupa por parentId num Map, percorre as raízes
     * e constrói cada nó recursivamente.
     *
     * @param categories todas as categorias ativas (já ordenadas por sortOrder)
     * @return lista de nós raiz com filhos aninhados
     */

    public static List<ListAllCategoriesOutput> fromTree(final List<Category> categories){
        final Map<String, List<Category>> byParent = categories.stream().collect(Collectors.groupingBy(
                c -> c.getParentId() != null ? c.getParentId().getValue().toString() : "__ROOT__"
        ));
        return byParent.getOrDefault("__ROOT__", List.of()).stream()
                .map(root -> buildNode(root, byParent))
                .toList();
    }

    private static ListAllCategoriesOutput buildNode(
            final Category category,
            final Map<String, List<Category>> byParent
    ) {
        final String id = category.getId().getValue().toString();

        final List<ListAllCategoriesOutput> children = byParent
                .getOrDefault(id, List.of())
                .stream()
                .map(child -> buildNode(child, byParent))
                .toList();

        return new ListAllCategoriesOutput(
                id,
                category.getParentId() != null ? category.getParentId().getValue().toString() : null,
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getImageUrl(),
                category.getSortOrder(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                children
        );
    }
}
