package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.address.list_address.AddressOutputItem;
import com.btree.application.usecase.user.address.list_address.ListAddressOutput;

import java.time.Instant;
import java.util.List;

public record ListAddressResponse(List<Item> items) {

    public static ListAddressResponse from(final ListAddressOutput output) {
        final var items = output.items().stream()
                .map(Item::from)
                .toList();

        return new ListAddressResponse(items);
    }

    public record Item(
            String id,
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
        public static Item from(final AddressOutputItem item) {
            return new Item(
                    item.id(),
                    item.label(),
                    item.recipientName(),
                    item.street(),
                    item.number(),
                    item.complement(),
                    item.neighborhood(),
                    item.city(),
                    item.state(),
                    item.postalCode(),
                    item.country(),
                    item.isDefault(),
                    item.isBillingAddress(),
                    item.createdAt(),
                    item.updatedAt()
            );
        }
    }
}