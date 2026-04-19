package com.btree.domain.catalog.value_object;

import com.btree.shared.domain.ValueObject;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object representing the physical dimensions and weight of a product.
 *
 * <p>Maps to {@code catalog.products}: weight DECIMAL(8,3), width DECIMAL(8,2),
 * height DECIMAL(8,2), depth DECIMAL(8,2). All values are optional (nullable).
 */
public class ProductDimensions extends ValueObject {

    private final BigDecimal weight;
    private final BigDecimal width;
    private final BigDecimal height;
    private final BigDecimal depth;

    private ProductDimensions(
            final BigDecimal weight,
            final BigDecimal width,
            final BigDecimal height,
            final BigDecimal depth
    ) {
        if (weight != null && weight.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("'weight' não pode ser negativo");
        }
        if (width != null && width.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("'width' não pode ser negativo");
        }
        if (height != null && height.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("'height' não pode ser negativo");
        }
        if (depth != null && depth.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("'depth' não pode ser negativo");
        }
        this.weight = weight;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public static ProductDimensions of(
            final BigDecimal weight,
            final BigDecimal width,
            final BigDecimal height,
            final BigDecimal depth
    ) {
        return new ProductDimensions(weight, width, height, depth);
    }

    public static ProductDimensions empty() {
        return new ProductDimensions(null, null, null, null);
    }

    public boolean isEmpty() {
        return weight == null && width == null && height == null && depth == null;
    }

    public BigDecimal getWeight() { return weight; }
    public BigDecimal getWidth() { return width; }
    public BigDecimal getHeight() { return height; }
    public BigDecimal getDepth() { return depth; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ProductDimensions that = (ProductDimensions) o;
        return Objects.equals(weight, that.weight)
                && Objects.equals(width, that.width)
                && Objects.equals(height, that.height)
                && Objects.equals(depth, that.depth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weight, width, height, depth);
    }
}
