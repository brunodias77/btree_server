package com.btree.infrastructure.catalog.persistence;

import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.gateway.ProductGateway;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.domain.catalog.identifier.ProductId;
import com.btree.domain.catalog.persistence.query.ProductSearchQuery;
import com.btree.infrastructure.catalog.entity.ProductJpaEntity;
import com.btree.shared.enums.ProductStatus;
import com.btree.shared.exception.NotFoundException;
import com.btree.shared.pagination.PageRequest;
import com.btree.shared.pagination.Pagination;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Transactional
public class ProductPostgresGateway implements ProductGateway {

    private final ProductJpaRepository productJpaRepository;

    public ProductPostgresGateway(final ProductJpaRepository productJpaRepository) {
        this.productJpaRepository = productJpaRepository;
    }

    @Override
    public Product save(final Product product) {
        return productJpaRepository
                .save(ProductJpaEntity.from(product))
                .toAggregate();
    }

    @Override
    public Product update(final Product product) {
        final var entity = productJpaRepository.findById(product.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(Product.class, product.getId().getValue()));
        entity.updateFrom(product);
        return productJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(final ProductId id) {
        return productJpaRepository.findById(id.getValue())
                .map(ProductJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllByIds(final List<ProductId> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        final var uuids = ids.stream()
                .map(ProductId::getValue)
                .collect(Collectors.toList());
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(uuids)
                .stream()
                .map(ProductJpaEntity::toAggregate)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Product> findByIdForUpdate(final ProductId id) {
        return productJpaRepository.findByIdForUpdate(id.getValue())
                .map(ProductJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findBySlug(final String slug) {
        return productJpaRepository.findBySlugAndDeletedAtIsNull(slug)
                .map(ProductJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findBySku(final String sku) {
        return productJpaRepository.findBySkuAndDeletedAtIsNull(sku)
                .map(ProductJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(final String slug) {
        return productJpaRepository.existsBySlugAndDeletedAtIsNull(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySku(final String sku) {
        return productJpaRepository.existsBySkuAndDeletedAtIsNull(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlugExcludingId(final String slug, final ProductId id) {
        return productJpaRepository.existsBySlugAndIdNotAndDeletedAtIsNull(slug, id.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySkuExcludingId(final String sku, final ProductId id) {
        return productJpaRepository.existsBySkuAndIdNotAndDeletedAtIsNull(sku, id.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<Product> findAll(final PageRequest pageRequest) {
        final var page = productJpaRepository.findAllByDeletedAtIsNull(toPageable(pageRequest));
        return toPagination(page, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<Product> findByCategory(final CategoryId categoryId, final PageRequest pageRequest) {
        final var page = productJpaRepository.findByCategoryIdAndDeletedAtIsNull(categoryId.getValue(), toPageable(pageRequest));
        return toPagination(page, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<Product> findByBrand(final BrandId brandId, final PageRequest pageRequest) {
        final var page = productJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId.getValue(), toPageable(pageRequest));
        return toPagination(page, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<Product> findByStatus(final ProductStatus status, final PageRequest pageRequest) {
        final var page = productJpaRepository.findByStatusAndDeletedAtIsNull(status, toPageable(pageRequest));
        return toPagination(page, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<Product> findActiveByCategoryId(
            final CategoryId categoryId, final PageRequest pageRequest) {
        final var pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(), pageRequest.size(), Sort.by(Sort.Direction.ASC, "name"));
        final var page = productJpaRepository.findByCategoryIdAndStatusAndDeletedAtIsNull(
                categoryId.getValue(), ProductStatus.ACTIVE, pageable);
        return toPagination(page, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<Product> search(final ProductSearchQuery query, final PageRequest pageRequest) {
        final var spec     = ProductSpecifications.from(query);
        final var pageable = toPageable(pageRequest);
        final var page     = productJpaRepository.findAll(spec, pageable);
        return toPagination(page, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<Product> findActiveByBrandId(
            final BrandId brandId, final PageRequest pageRequest) {
        final var pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(), pageRequest.size(), Sort.by(Sort.Direction.ASC, "name"));
        final var page = productJpaRepository.findByBrandIdAndStatusAndDeletedAtIsNull(
                brandId.getValue(), ProductStatus.ACTIVE, pageable);
        return toPagination(page, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<Product> findFeatured(final PageRequest pageRequest) {
        final var pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(), pageRequest.size(), Sort.by(Sort.Direction.ASC, "name"));
        final var page = productJpaRepository.findByFeaturedTrueAndStatusAndDeletedAtIsNull(
                ProductStatus.ACTIVE, pageable);
        return toPagination(page, pageRequest);
    }

    @Override
    public void deleteById(final ProductId id) {
        final var entity = productJpaRepository.findById(id.getValue())
                .orElseThrow(() -> NotFoundException.with(Product.class, id.getValue()));
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        productJpaRepository.save(entity);
    }

    // ── Helpers ───────────────────────────────────────────────

    private static org.springframework.data.domain.PageRequest toPageable(final PageRequest req) {
        return org.springframework.data.domain.PageRequest.of(
                req.page(), req.size(), Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    private static Pagination<Product> toPagination(
            final org.springframework.data.domain.Page<ProductJpaEntity> page,
            final PageRequest req
    ) {
        return Pagination.of(
                page.getContent().stream().map(ProductJpaEntity::toAggregate).toList(),
                req.page(),
                req.size(),
                page.getTotalElements()
        );
    }
}
