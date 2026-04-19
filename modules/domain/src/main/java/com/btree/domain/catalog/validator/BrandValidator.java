package com.btree.domain.catalog.validator;


import com.btree.domain.catalog.entity.Brand;
import com.btree.domain.catalog.error.BrandError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;

import java.util.regex.Pattern;

/**
 * Validador das invariantes da entidade Brand.
 */
public class BrandValidator extends Validator {

    private static final int NAME_MAX_LENGTH = 200;
    private static final int SLUG_MAX_LENGTH = 256;

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final Brand brand;

    public BrandValidator(final Brand brand, final ValidationHandler handler) {
        super(handler);
        this.brand = brand;
    }

    @Override
    public void validate() {
        checkName();
        checkSlug();
    }

    private void checkName() {
        final var name = this.brand.getName();
        if (name == null || name.isBlank()) {
            validationHandler().append(BrandError.NAME_EMPTY);
            return;
        }
        if (name.length() > NAME_MAX_LENGTH) {
            validationHandler().append(BrandError.NAME_TOO_LONG);
        }
    }

    private void checkSlug() {
        final var slug = this.brand.getSlug();
        if (slug == null || slug.isBlank()) {
            validationHandler().append(BrandError.SLUG_EMPTY);
            return;
        }
        if (slug.length() > SLUG_MAX_LENGTH) {
            validationHandler().append(BrandError.SLUG_TOO_LONG);
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            validationHandler().append(BrandError.SLUG_INVALID_FORMAT);
        }
    }
}
