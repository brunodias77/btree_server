package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.address.add_address.AddAddressOutput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO HTTP de saída — representa um endereço.
 * Usado como resposta do {@code POST} (UC-17) e reutilizado
 * nos demais endpoints do {@code AddressController}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddAddressResponse(
        String id,
        @JsonProperty("user_id")            String userId,
        String label,
        @JsonProperty("recipient_name")     String recipientName,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        @JsonProperty("postal_code")        String postalCode,
        String country,
        @JsonProperty("is_default")         boolean isDefault,
        @JsonProperty("is_billing_address") boolean isBillingAddress,
        @JsonProperty("created_at") Instant createdAt
) {
    public static AddAddressResponse from(final AddAddressOutput output) {
        return new AddAddressResponse(
                output.id(),
                output.userId(),
                output.label(),
                output.recipientName(),
                output.street(),
                output.number(),
                output.complement(),
                output.neighborhood(),
                output.city(),
                output.state(),
                output.postalCode(),
                output.country(),
                output.isDefault(),
                output.isBillingAddress(),
                output.createdAt()
        );
    }
}