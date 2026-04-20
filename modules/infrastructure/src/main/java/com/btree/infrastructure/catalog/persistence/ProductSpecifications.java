package com.btree.infrastructure.catalog.persistence;


import com.btree.domain.catalog.persistence.query.ProductSearchQuery;
import com.btree.infrastructure.catalog.entity.ProductJpaEntity;
import com.btree.shared.enums.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fábrica de {@link Specification} para busca dinâmica de produtos.
 *
 * <p>Cada método retorna {@code null} quando o filtro não está presente —
 * {@code Specification.allOf()} ignora automaticamente elementos {@code null}.
 */
public class ProductSpecifications {

    private ProductSpecifications() {}

    public static Specification<ProductJpaEntity> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<ProductJpaEntity> withTerm(final String term) {
        if (term == null || term.isBlank()) return null;
        final var pattern = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("sku")), pattern),
                cb.like(cb.lower(root.get("shortDescription")), pattern)
        );
    }

    public static Specification<ProductJpaEntity> withCategory(final String categoryId) {
        if (categoryId == null) return null;
        return (root, query, cb) ->
                cb.equal(root.get("categoryId"), UUID.fromString(categoryId));
    }

    public static Specification<ProductJpaEntity> withBrand(final String brandId) {
        if (brandId == null) return null;
        return (root, query, cb) ->
                cb.equal(root.get("brandId"), UUID.fromString(brandId));
    }

    public static Specification<ProductJpaEntity> withMinPrice(final BigDecimal min) {
        if (min == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<ProductJpaEntity> withMaxPrice(final BigDecimal max) {
        if (max == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<ProductJpaEntity> withStatus(final ProductStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<ProductJpaEntity> withFeatured(final Boolean featured) {
        if (featured == null) return null;
        return (root, query, cb) -> cb.equal(root.get("featured"), featured);
    }

    /** Combina todos os critérios de um {@link ProductSearchQuery} em uma Specification única. */
    public static Specification<ProductJpaEntity> from(final ProductSearchQuery q) {
        return Specification.allOf(
                notDeleted(),
                withTerm(q.term()),
                withCategory(q.categoryId()),
                withBrand(q.brandId()),
                withMinPrice(q.minPrice()),
                withMaxPrice(q.maxPrice()),
                withStatus(q.status()),
                withFeatured(q.featured())
        );
    }
}

