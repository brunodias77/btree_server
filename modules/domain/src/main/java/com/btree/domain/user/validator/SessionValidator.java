package com.btree.domain.user.validator;

import com.btree.domain.user.entity.Session;
import com.btree.domain.user.error.SessionError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;

public class SessionValidator extends Validator {

    private final Session session;

    public SessionValidator(final Session session, final ValidationHandler handler) {
        super(handler);
        this.session = session;
    }

    @Override
    public void validate() {
        checkUserId();
        checkRefreshTokenHash();
        checkExpiresAt();
    }

    private void checkUserId() {
        if (session.getUserId() == null) {
            validationHandler().append(SessionError.USER_ID_NULL);
        }
    }

    private void checkRefreshTokenHash() {
        if (session.getRefreshTokenHash() == null || session.getRefreshTokenHash().isBlank()) {
            validationHandler().append(SessionError.REFRESH_TOKEN_EMPTY);
        }
    }

    private void checkExpiresAt() {
        if (session.getExpiresAt() == null) {
            validationHandler().append(SessionError.EXPIRES_AT_NULL);
        }
    }
}
