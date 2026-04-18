package com.btree.application.usecase.user.get_current_user;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.entity.User;

import java.time.Instant;
import java.util.List;

/**
 * Output do caso de uso UC-05 — GetCurrentUser.
 *
 * <p>Omite campos sensíveis (passwordHash, twoFactorSecret, etc.).
 */
public record GetCurrentUserOutput(
        String id,
        String username,
        String email,
        boolean emailVerified,
        List<String> roles,
        ProfileOutput profile,
        Instant createdAt
) {

    public record ProfileOutput(
            String firstName,
            String lastName,
            String displayName,
            String avatarUrl,
            String preferredLanguage,
            String preferredCurrency
    ) {
        public static ProfileOutput from(final Profile profile) {
            if (profile == null) return null;
            return new ProfileOutput(
                    profile.getFirstName(),
                    profile.getLastName(),
                    profile.getDisplayName(),
                    profile.getAvatarUrl(),
                    profile.getPreferredLanguage(),
                    profile.getPreferredCurrency()
            );
        }
    }

    public static GetCurrentUserOutput from(final User user) {
        return new GetCurrentUserOutput(
                user.getId().getValue().toString(),
                user.getUsername(),
                user.getEmail(),
                user.isEmailVerified(),
                List.copyOf(user.getRoles()),
                ProfileOutput.from(user.getProfile()),
                user.getCreatedAt()
        );
    }
}
