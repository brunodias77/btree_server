package com.btree.infrastructure.catalog.persistence;

import com.btree.domain.catalog.entity.Category;
import com.btree.domain.catalog.gateway.CategoryGateway;
import com.btree.domain.catalog.identifier.CategoryId;
import com.btree.infrastructure.catalog.entity.CategoryJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class CategoryPostgresGateway implements CategoryGateway {

    private final CategoryJpaRepository categoryJpaRepository;

    public CategoryPostgresGateway(final CategoryJpaRepository categoryJpaRepository) {
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Override
    public Category save(final Category category) {
        return categoryJpaRepository
                .save(CategoryJpaEntity.from(category))
                .toAggregate();
    }

    @Override
    public Category update(final Category category) {
        final var entity = categoryJpaRepository.findById(category.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(Category.class, category.getId().getValue()));
        entity.updateFrom(category);
        return categoryJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(final CategoryId id) {
        return categoryJpaRepository.findById(id.getValue())
                .map(CategoryJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findBySlug(final String slug) {
        return categoryJpaRepository.findBySlugAndDeletedAtIsNull(slug)
                .map(CategoryJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(final String slug) {
        return categoryJpaRepository.existsBySlugAndDeletedAtIsNull(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlugExcluding(final String slug, final CategoryId excludeId) {
        return categoryJpaRepository
                .existsBySlugAndDeletedAtIsNullAndIdNot(slug, excludeId.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryJpaRepository.findAllActive().stream()
                .map(CategoryJpaEntity::toAggregate)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findByParentId(final CategoryId parentId) {
        return categoryJpaRepository.findActiveByParentId(parentId.getValue()).stream()
                .map(CategoryJpaEntity::toAggregate)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findRoots() {
        return categoryJpaRepository.findRoots().stream()
                .map(CategoryJpaEntity::toAggregate)
                .toList();
    }

    @Override
    public void deleteById(final CategoryId id) {
        final var entity = categoryJpaRepository.findById(id.getValue())
                .orElseThrow(() -> NotFoundException.with(Category.class, id.getValue()));
        entity.setDeletedAt(Instant.now());
        entity.setActive(false);
        categoryJpaRepository.save(entity);
    }
}
