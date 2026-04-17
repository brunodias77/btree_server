package com.btree.api.dto.response.user;

import com.btree.application.usecase.user.get_current_user.GetCurrentUserOutput;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record CurrentUserResponse(
        String id,
        String username,
        String email,
        @JsonProperty("email_verified") boolean emailVerified,
        List<String> roles,
        ProfileResponse profile,
        @JsonProperty("created_at") Instant createdAt
) {

    public record ProfileResponse(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("preferred_language") String preferredLanguage,
            @JsonProperty("preferred_currency") String preferredCurrency
    ) {
        public static ProfileResponse from(final GetCurrentUserOutput.ProfileOutput profile) {
            if (profile == null) return null;
            return new ProfileResponse(
                    profile.firstName(),
                    profile.lastName(),
                    profile.displayName(),
                    profile.avatarUrl(),
                    profile.preferredLanguage(),
                    profile.preferredCurrency()
            );
        }
    }

    public static CurrentUserResponse from(final GetCurrentUserOutput output) {
        return new CurrentUserResponse(
                output.id(),
                output.username(),
                output.email(),
                output.emailVerified(),
                output.roles(),
                ProfileResponse.from(output.profile()),
                output.createdAt()
        );
    }
}
