package com.btree.domain.catalog.gateway;

import com.btree.domain.catalog.entity.Brand;
import com.btree.domain.catalog.identifier.BrandId;

import java.util.List;
import java.util.Optional;

public interface BrandGateway {

    Brand save(Brand brand);

    Brand update(Brand brand);

    Optional<Brand> findById(BrandId id);

    Optional<Brand> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugExcluding(String slug, BrandId excludeId);

    List<Brand> findAll();

    void deleteById(BrandId id);
}

