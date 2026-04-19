package com.btree.application.usecase.user.address.list_address;

import com.btree.domain.user.entity.Address;

import java.time.Instant;
import java.util.Objects;

public record AddressOutputItem(
        String id,
        String userId,
        String label,
        String recipientName,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isDefault,
        boolean isBillingAddress,
        Instant createdAt,
        Instant updatedAt
) {

    public static AddressOutputItem from(final Address address) {
        Objects.requireNonNull(address, "address must not be null");

        return new AddressOutputItem(
                asString(address.getId()),
                asString(address.getUserId()),
                address.getLabel(),
                address.getRecipientName(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.isDefault(),
                address.isBillingAddress(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }

    private static String asString(final Object value) {
        return value == null ? null : value.toString();
    }
}