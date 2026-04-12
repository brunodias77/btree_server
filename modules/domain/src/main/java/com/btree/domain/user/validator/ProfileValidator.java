package com.btree.domain.user.validator;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.error.ProfileError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;
import com.btree.shared.valueobject.Cpf;

public class ProfileValidator extends Validator {

    private static final int NAME_MAX_LENGTH     = 100;
    private static final int LANGUAGE_MAX_LENGTH = 10;
    private static final int LANGUAGE_MIN_LENGTH = 2;
    private static final int CURRENCY_LENGTH     = 3;

    private final Profile profile;

    public ProfileValidator(final Profile profile, final ValidationHandler handler) {
        super(handler);
        this.profile = profile;
    }

    @Override
    public void validate() {
        checkFirstName();
        checkLastName();
        checkCpf();
        checkPreferredLanguage();
        checkPreferredCurrency();
    }

    private void checkFirstName() {
        final var firstName = profile.getFirstName();
        if (firstName != null && firstName.length() > NAME_MAX_LENGTH) {
            validationHandler().append(ProfileError.NAME_TOO_LONG);
        }
    }

    private void checkLastName() {
        final var lastName = profile.getLastName();
        if (lastName != null && lastName.length() > NAME_MAX_LENGTH) {
            validationHandler().append(ProfileError.NAME_TOO_LONG);
        }
    }

    private void checkCpf() {
        final var cpf = profile.getCpf();
        if (cpf != null && !cpf.isBlank()) {
            try {
                Cpf.of(cpf);
            } catch (IllegalArgumentException e) {
                validationHandler().append(ProfileError.CPF_INVALID);
            }
        }
    }

    private void checkPreferredLanguage() {
        final var lang = profile.getPreferredLanguage();
        if (lang != null && (lang.length() < LANGUAGE_MIN_LENGTH || lang.length() > LANGUAGE_MAX_LENGTH)) {
            validationHandler().append(ProfileError.PREFERRED_LANGUAGE_INVALID);
        }
    }

    private void checkPreferredCurrency() {
        final var currency = profile.getPreferredCurrency();
        if (currency != null && currency.length() != CURRENCY_LENGTH) {
            validationHandler().append(ProfileError.PREFERRED_CURRENCY_INVALID);
        }
    }
}
