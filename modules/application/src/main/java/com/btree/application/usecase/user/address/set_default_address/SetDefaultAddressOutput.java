package com.btree.application.usecase.user.address.set_default_address;

import com.btree.domain.user.entity.Address;

import java.time.Instant;

/**
 * Saída do caso de uso UC-20 — SetDefaultAddress.
 *
 * <p>Retorna o endereço completo após ser marcado como padrão,
 * permitindo que o cliente atualize seu estado local sem
 * necessidade de uma chamada adicional de leitura.
 */
public record SetDefaultAddressOutput(
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
    public static SetDefaultAddressOutput from(final Address address) {
        return new SetDefaultAddressOutput(
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
