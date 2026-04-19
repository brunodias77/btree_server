package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.get_profile.GetProfileOutput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetProfileResponse(
        String id,
        @JsonProperty("user_id")               String userId,
        @JsonProperty("first_name")            String firstName,
        @JsonProperty("last_name")             String lastName,
        @JsonProperty("display_name")          String displayName,
        @JsonProperty("avatar_url")            String avatarUrl,
        @JsonProperty("birth_date")            LocalDate birthDate,
        String gender,
        String cpf,
        @JsonProperty("preferred_language")    String preferredLanguage,
        @JsonProperty("preferred_currency")    String preferredCurrency,
        @JsonProperty("newsletter_subscribed") boolean newsletterSubscribed,
        @JsonProperty("accepted_terms_at")     Instant acceptedTermsAt,
        @JsonProperty("accepted_privacy_at")   Instant acceptedPrivacyAt,
        @JsonProperty("created_at")            Instant createdAt,
        @JsonProperty("updated_at")            Instant updatedAt
) {
    public static GetProfileResponse from(final GetProfileOutput output) {
        return new GetProfileResponse(
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
                output.acceptedTermsAt(),
                output.acceptedPrivacyAt(),
                output.createdAt(),
                output.updatedAt()
        );
    }
}