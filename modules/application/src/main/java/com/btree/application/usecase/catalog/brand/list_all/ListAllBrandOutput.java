package com.btree.application.usecase.catalog.brand.list_all;

import com.btree.domain.catalog.entity.Brand;

import java.util.List;
import java.util.Objects;

public record ListAllBrandOutput(List<BrandOutputItem> items) {

    public static ListAllBrandOutput from(final List<Brand> brands) {
        final var items = brands == null
                ? List.<BrandOutputItem>of()
                : brands.stream()
                        .filter(Objects::nonNull)
                        .map(BrandOutputItem::from)
                        .toList();
        return new ListAllBrandOutput(items);
    }
}
