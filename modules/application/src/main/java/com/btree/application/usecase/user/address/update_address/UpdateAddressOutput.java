package com.btree.application.usecase.user.address.update_address;

import com.btree.domain.user.entity.Address;

import java.time.Instant;

/**
 * Saída do caso de uso UC-18 — UpdateAddress.
 */
public record UpdateAddressOutput(
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
    public static UpdateAddressOutput from(final Address address) {
        return new UpdateAddressOutput(
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
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}

