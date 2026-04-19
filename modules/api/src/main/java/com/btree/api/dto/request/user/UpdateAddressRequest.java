package com.btree.api.dto.request.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAddressRequest(
        @Size(max = 50, message = "label deve ter no máximo 50 caracteres")
        String label,

        @Size(max = 150, message = "recipientName deve ter no máximo 150 caracteres")
        @JsonProperty("recipient_name")
        String recipientName,

        @NotBlank(message = "street é obrigatório")
        @Size(max = 255, message = "street deve ter no máximo 255 caracteres")
        String street,

        @Size(max = 20, message = "number deve ter no máximo 20 caracteres")
        String number,

        @Size(max = 100, message = "complement deve ter no máximo 100 caracteres")
        String complement,

        @Size(max = 100, message = "neighborhood deve ter no máximo 100 caracteres")
        String neighborhood,

        @NotBlank(message = "city é obrigatório")
        @Size(max = 100, message = "city deve ter no máximo 100 caracteres")
        String city,

        @NotBlank(message = "state é obrigatório")
        @Pattern(regexp = "^[A-Z]{2}$", message = "state deve conter exatamente 2 letras maiúsculas")
        String state,

        @NotBlank(message = "postalCode é obrigatório")
        @Pattern(
                regexp = "^\\d{5}-?\\d{3}$",
                message = "postalCode deve estar no formato XXXXX-XXX ou XXXXXXXX"
        )
        @JsonProperty("postal_code")
        String postalCode,

        @Size(min = 2, max = 2, message = "country deve ter exatamente 2 caracteres")
        String country,

        @JsonProperty("is_billing_address")
        boolean isBillingAddress
) {
}
