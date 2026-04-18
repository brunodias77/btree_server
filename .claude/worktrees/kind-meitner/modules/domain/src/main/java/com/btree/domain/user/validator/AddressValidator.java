package com.btree.domain.user.validator;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.error.AddressError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;
import com.btree.shared.valueobject.PostalCode;

public class AddressValidator extends Validator {

    private static final int STREET_MAX_LENGTH = 255;
    private static final int CITY_MAX_LENGTH   = 100;
    private static final String STATE_REGEX    = "^[A-Z]{2}$";

    private final Address address;

    public AddressValidator(final Address address, final ValidationHandler handler) {
        super(handler);
        this.address = address;
    }

    @Override
    public void validate() {
        checkUserId();
        checkStreet();
        checkCity();
        checkState();
        checkPostalCode();
        checkCountry();
    }

    private void checkUserId() {
        if (address.getUserId() == null) {
            validationHandler().append(AddressError.USER_ID_NULL);
        }
    }

    private void checkStreet() {
        final var street = address.getStreet();
        if (street == null || street.isBlank()) {
            validationHandler().append(AddressError.STREET_EMPTY);
            return;
        }
        if (street.length() > STREET_MAX_LENGTH) {
            validationHandler().append(AddressError.STREET_TOO_LONG);
        }
    }

    private void checkCity() {
        final var city = address.getCity();
        if (city == null || city.isBlank()) {
            validationHandler().append(AddressError.CITY_EMPTY);
            return;
        }
        if (city.length() > CITY_MAX_LENGTH) {
            validationHandler().append(AddressError.CITY_TOO_LONG);
        }
    }

    private void checkState() {
        final var state = address.getState();
        if (state == null || !state.matches(STATE_REGEX)) {
            validationHandler().append(AddressError.STATE_INVALID);
        }
    }

    private void checkPostalCode() {
        final var postalCode = address.getPostalCode();
        if (postalCode == null || postalCode.isBlank()) {
            validationHandler().append(AddressError.POSTAL_CODE_INVALID);
            return;
        }
        try {
            PostalCode.of(postalCode);
        } catch (IllegalArgumentException e) {
            validationHandler().append(AddressError.POSTAL_CODE_INVALID);
        }
    }

    private void checkCountry() {
        final var country = address.getCountry();
        if (country == null || country.isBlank()) {
            validationHandler().append(AddressError.COUNTRY_EMPTY);
        }
    }
}
