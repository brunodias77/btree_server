package com.btree.domain.catalog.gateway;

import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.persistence.query.ProductSearchQuery;
import com.btree.shared.enums.ProductStatus;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.pagination.Pagination;

import java.util.List;
import java.util.Optional;

public interface ProductGateway {

    Product save(Product product);

    Product update(Product product);

    Optional<Product> findById(ProductId id);

    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySku(String sku);

    boolean existsBySlug(String slug);

    boolean existsBySku(String sku);

    boolean existsBySlugExcludingId(String slug, ProductId id);

    boolean existsBySkuExcludingId(String sku, ProductId id);

    Pagination<Product> findAll(PageRequest pageRequest);

    Pagination<Product> findByCategory(CategoryId categoryId, PageRequest pageRequest);

    Pagination<Product> findByBrand(BrandId brandId, PageRequest pageRequest);

    Pagination<Product> findByStatus(ProductStatus status, PageRequest pageRequest);

    Pagination<Product> search(ProductSearchQuery query, PageRequest pageRequest);

    /**
     * Retorna produtos com {@code status = ACTIVE} e não-deletados de uma categoria, paginados.
     * Usado por endpoints públicos de vitrine.
     */
    Pagination<Product> findActiveByCategoryId(CategoryId categoryId, PageRequest pageRequest);

    /**
     * Retorna produtos com {@code status = ACTIVE} e não-deletados de uma marca, paginados.
     * Usado por endpoints públicos de vitrine.
     */
    Pagination<Product> findActiveByBrandId(BrandId brandId, PageRequest pageRequest);

    /**
     * Retorna produtos com {@code featured = true}, {@code status = ACTIVE} e não-deletados, paginados.
     * Usado por seções de destaque na vitrine.
     */
    Pagination<Product> findFeatured(PageRequest pageRequest);

    /**
     * Busca múltiplos produtos pelos IDs em uma única query (evita N+1 em listagens).
     * Produtos deletados ({@code deleted_at IS NOT NULL}) são excluídos do resultado.
     * IDs não encontrados simplesmente não aparecem no retorno — sem exceção.
     */
    List<Product> findAllByIds(List<ProductId> ids);

    /**
     * Busca o produto pelo ID adquirindo lock pessimista (SELECT FOR UPDATE).
     * Usar exclusivamente dentro de uma transação ativa, em operações concorrentes de estoque.
     */
    Optional<Product> findByIdForUpdate(ProductId id);

    void deleteById(ProductId id);
}
