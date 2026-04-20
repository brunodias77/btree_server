package com.btree.domain.catalog.validator;

import com.btree.domain.catalog.entity.Product;
import com.btree.domain.catalog.error.ProductError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Validador das invariantes do agregado Product.
 */
public class ProductValidator extends Validator {

    private static final int NAME_MAX_LENGTH = 300;
    private static final int SLUG_MAX_LENGTH = 350;
    private static final int SKU_MAX_LENGTH = 50;
    private static final int SHORT_DESCRIPTION_MAX_LENGTH = 500;

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");

    private final Product product;

    public ProductValidator(final Product product, final ValidationHandler handler) {
        super(handler);
        this.product = product;
    }

    @Override
    public void validate() {
        checkName();
        checkSlug();
        checkSku();
        checkPrice();
        checkQuantity();
        checkShortDescription();
    }

    private void checkName() {
        final var name = this.product.getName();
        if (name == null || name.isBlank()) {
            validationHandler().append(ProductError.NAME_EMPTY);
            return;
        }
        if (name.length() > NAME_MAX_LENGTH) {
            validationHandler().append(ProductError.NAME_TOO_LONG);
        }
    }

    private void checkSlug() {
        final var slug = this.product.getSlug();
        if (slug == null || slug.isBlank()) {
            validationHandler().append(ProductError.SLUG_EMPTY);
            return;
        }
        if (slug.length() > SLUG_MAX_LENGTH) {
            validationHandler().append(ProductError.SLUG_TOO_LONG);
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            validationHandler().append(ProductError.SLUG_INVALID_FORMAT);
        }
    }

    private void checkSku() {
        final var sku = this.product.getSku();
        if (sku == null || sku.isBlank()) {
            validationHandler().append(ProductError.SKU_EMPTY);
            return;
        }
        if (sku.length() > SKU_MAX_LENGTH) {
            validationHandler().append(ProductError.SKU_TOO_LONG);
        }
        if (!SKU_PATTERN.matcher(sku).matches()) {
            validationHandler().append(ProductError.SKU_INVALID_FORMAT);
        }
    }

    private void checkPrice() {
        final var price = this.product.getPrice();
        if (price == null) {
            validationHandler().append(ProductError.PRICE_NULL);
            return;
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            validationHandler().append(ProductError.PRICE_NEGATIVE);
        }
    }

    private void checkQuantity() {
        if (this.product.getQuantity() < 0) {
            validationHandler().append(ProductError.QUANTITY_NEGATIVE);
        }
        if (this.product.getLowStockThreshold() < 0) {
            validationHandler().append(ProductError.LOW_STOCK_THRESHOLD_NEGATIVE);
        }
    }

    private void checkShortDescription() {
        final var shortDesc = this.product.getShortDescription();
        if (shortDesc != null && shortDesc.length() > SHORT_DESCRIPTION_MAX_LENGTH) {
            validationHandler().append(ProductError.SHORT_DESCRIPTION_TOO_LONG);
        }
    }
}
