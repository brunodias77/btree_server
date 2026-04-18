package com.btree.api.dto.request.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @Size(max = 100, message = "firstName deve ter no máximo 100 caracteres")
        @JsonProperty("first_name")
        String firstName,

        @Size(max = 100, message = "lastName deve ter no máximo 100 caracteres")
        @JsonProperty("last_name")
        String lastName,

        @Pattern(
                regexp = "^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$",
                message = "cpf deve estar no formato XXX.XXX.XXX-XX"
        )
        String cpf,

        @JsonProperty("birth_date")
        LocalDate birthDate,

        @Size(max = 20, message = "gender deve ter no máximo 20 caracteres")
        String gender,

        @Size(min = 2, max = 10, message = "preferredLanguage deve ter entre 2 e 10 caracteres")
        @JsonProperty("preferred_language")
        String preferredLanguage,

        @Size(min = 3, max = 3, message = "preferredCurrency deve ter exatamente 3 caracteres")
        @JsonProperty("preferred_currency")
        String preferredCurrency,

        @JsonProperty("newsletter_subscribed")
        boolean newsletterSubscribed
) {
}
