package com.btree.application.usecase.catalog.category.get_by_id;

/**
 * Comando de entrada para UC-47 — GetCategory.
 *
 * @param categoryId UUID da categoria a consultar (extraído do path variable)
 */
public record GetCategoryCommand(String categoryId) {}