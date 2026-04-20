package com.btree.domain.catalog.validator;

import com.btree.domain.catalog.entity.ProductReview;
import com.btree.domain.catalog.error.ProductReviewError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;

/**
 * Validador das invariantes da entidade ProductReview.
 */
public class ProductReviewValidator extends Validator {

    private static final int TITLE_MAX_LENGTH = 200;

    private final ProductReview review;

    public ProductReviewValidator(final ProductReview review, final ValidationHandler handler) {
        super(handler);
        this.review = review;
    }

    @Override
    public void validate() {
        checkProductId();
        checkUserId();
        checkRating();
        checkTitle();
    }

    private void checkProductId() {
        if (this.review.getProductId() == null) {
            validationHandler().append(ProductReviewError.PRODUCT_ID_NULL);
        }
    }

    private void checkUserId() {
        if (this.review.getUserId() == null) {
            validationHandler().append(ProductReviewError.USER_ID_NULL);
        }
    }

    private void checkRating() {
        final int rating = this.review.getRating();
        if (rating < 1 || rating > 5) {
            validationHandler().append(ProductReviewError.RATING_INVALID);
        }
    }

    private void checkTitle() {
        final var title = this.review.getTitle();
        if (title != null && title.length() > TITLE_MAX_LENGTH) {
            validationHandler().append(ProductReviewError.TITLE_TOO_LONG);
        }
    }
}
