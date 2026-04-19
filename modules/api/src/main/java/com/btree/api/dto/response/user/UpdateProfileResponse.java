package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.update_profile.UpdateProfileOutput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateProfileResponse(
        String id,
        @JsonProperty("user_id")              String userId,
        @JsonProperty("first_name")           String firstName,
        @JsonProperty("last_name")            String lastName,
        @JsonProperty("display_name")         String displayName,
        @JsonProperty("avatar_url")           String avatarUrl,
        @JsonProperty("birth_date")           LocalDate birthDate,
        String gender,
        String cpf,
        @JsonProperty("preferred_language")   String preferredLanguage,
        @JsonProperty("preferred_currency")   String preferredCurrency,
        @JsonProperty("newsletter_subscribed") boolean newsletterSubscribed,
        @JsonProperty("updated_at")           Instant updatedAt
) {

    public static UpdateProfileResponse from(final UpdateProfileOutput output) {
        return new UpdateProfileResponse(
                output.id(),
                output.userId(),
                output.firstName(),
                output.lastName(),
                output.displayName(),
                output.avatarUrl(),
                output.birthDate(),
                output.gender(),
                output.cpf(),
                output.preferredLanguage(),
                output.preferredCurrency(),
                output.newsletterSubscribed(),
                output.updatedAt()
        );
    }
}
