package com.btree.infrastructure.catalog.persistence;

import com.btree.domain.catalog.entity.Brand;
import com.btree.domain.catalog.gateway.BrandGateway;
import com.btree.domain.catalog.identifier.BrandId;
import com.btree.infrastructure.catalog.entity.BrandJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class BrandPostgresGateway implements BrandGateway {

    private final BrandJpaRepository brandJpaRepository;

    public BrandPostgresGateway(final BrandJpaRepository brandJpaRepository) {
        this.brandJpaRepository = brandJpaRepository;
    }

    @Override
    public Brand save(final Brand brand) {
        return brandJpaRepository
                .save(BrandJpaEntity.from(brand))
                .toAggregate();
    }

    @Override
    public Brand update(final Brand brand) {
        final var entity = brandJpaRepository.findById(brand.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(Brand.class, brand.getId().getValue()));
        entity.updateFrom(brand);
        return brandJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Brand> findById(final BrandId id) {
        return brandJpaRepository.findById(id.getValue())
                .map(BrandJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Brand> findBySlug(final String slug) {
        return brandJpaRepository.findBySlugAndDeletedAtIsNull(slug)
                .map(BrandJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(final String slug) {
        return brandJpaRepository.existsBySlugAndDeletedAtIsNull(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlugExcluding(final String slug, final BrandId excludeId) {
        return brandJpaRepository
                .existsBySlugAndDeletedAtIsNullAndIdNot(slug, excludeId.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Brand> findAll() {
        return brandJpaRepository.findAllActive().stream()
                .map(BrandJpaEntity::toAggregate)
                .toList();
    }

    @Override
    public void deleteById(final BrandId id) {
        final var entity = brandJpaRepository.findById(id.getValue())
                .orElseThrow(() -> NotFoundException.with(Brand.class, id.getValue()));
        entity.setDeletedAt(java.time.Instant.now());
        brandJpaRepository.save(entity);
    }
}
