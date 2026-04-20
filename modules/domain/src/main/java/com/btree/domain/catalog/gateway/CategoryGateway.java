package com.btree.domain.catalog.gateway;

import com.btree.domain.catalog.entity.Category;
import com.btree.domain.catalog.identifier.CategoryId;

import java.util.List;
import java.util.Optional;

public interface CategoryGateway {

    Category save(Category category);

    Category update(Category category);

    Optional<Category> findById(CategoryId id);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugExcluding(String slug, CategoryId excludeId);

    List<Category> findAll();

    List<Category> findByParentId(CategoryId parentId);

    List<Category> findRoots();

    void deleteById(CategoryId id);
}
