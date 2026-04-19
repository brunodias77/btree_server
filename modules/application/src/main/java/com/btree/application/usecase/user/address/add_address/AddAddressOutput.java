package com.btree.application.usecase.user.address.add_address;

import com.btree.domain.user.entity.Address;

import java.time.Instant;

/**
 * Saída do caso de uso UC-17 — AddAddress.
 */
public record AddAddressOutput(
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
        Instant createdAt
) {
    public static AddAddressOutput from(final Address address) {
        return new AddAddressOutput(
                address.getId().getValue().toString(),
                address.getUserId().getValue().toString(),
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
                address.getCreatedAt()
        );
    }
}
