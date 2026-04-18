package com.btree.application.usecase.user.update_profile;

import com.btree.domain.user.entity.Profile;

import java.time.Instant;
import java.time.LocalDate;

public record UpdateProfileOutput(
        String id,
        String userId,
        String firstName,
        String lastName,
        String displayName,
        String avatarUrl,
        LocalDate birthDate,
        String gender,
        String cpf,
        String preferredLanguage,
        String preferredCurrency,
        boolean newsletterSubscribed,
        Instant updatedAt
) {
    public static UpdateProfileOutput from(final Profile profile) {
        return new UpdateProfileOutput(
                profile.getId().getValue().toString(),
                profile.getUserId().getValue().toString(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getBirthDate(),
                profile.getGender(),
                profile.getCpf(),
                profile.getPreferredLanguage(),
                profile.getPreferredCurrency(),
                profile.isNewsletterSubscribed(),
                profile.getUpdatedAt()
        );
    }
}
