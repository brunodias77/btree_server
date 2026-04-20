package com.btree.application.usecase.catalog.category.create;

/**
 * Comando de entrada para UC-45 — CreateCategory.
 *
 * @param parentId    ID da categoria pai (nullable — ausente = categoria raiz)
 * @param name        nome da categoria
 * @param slug        slug único em formato kebab-case
 * @param description descrição longa (nullable)
 * @param imageUrl    URL da imagem representativa (nullable)
 * @param sortOrder   ordem de exibição (padrão 0)
 */
public record CreateCategoryCommand(
        String parentId,
        String name,
        String slug,
        String description,
        String imageUrl,
        int    sortOrder
) {}

