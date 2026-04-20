package com.btree.domain.catalog.validator;

import com.btree.domain.catalog.entity.Category;
import com.btree.domain.catalog.error.CategoryError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;


import java.util.regex.Pattern;

public class CategoryValidator extends Validator {

    private static final int NAME_MAX_LENGTH = 200;
    private static final int SLUG_MAX_LENGTH = 256;

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final Category category;

    public CategoryValidator(final Category category, final ValidationHandler handler) {
        super(handler);
        this.category = category;
    }

    @Override
    public void validate() {
        checkName();
        checkSlug();
    }

    private void checkName() {
        final var name = this.category.getName();
        if (name == null || name.isBlank()) {
            validationHandler().append(CategoryError.NAME_EMPTY);
            return;
        }
        if (name.length() > NAME_MAX_LENGTH) {
            validationHandler().append(CategoryError.NAME_TOO_LONG);
        }
    }

    private void checkSlug() {
        final var slug = this.category.getSlug();
        if (slug == null || slug.isBlank()) {
            validationHandler().append(CategoryError.SLUG_EMPTY);
            return;
        }
        if (slug.length() > SLUG_MAX_LENGTH) {
            validationHandler().append(CategoryError.SLUG_TOO_LONG);
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            validationHandler().append(CategoryError.SLUG_INVALID_FORMAT);
        }
    }
}
