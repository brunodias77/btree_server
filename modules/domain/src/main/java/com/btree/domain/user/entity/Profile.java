package com.btree.domain.user.entity;

import com.btree.domain.user.identifier.ProfileId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.time.LocalDate;

public class Profile extends Entity<ProfileId> {

    private final UserId userId;
    private String firstName;
    private String lastName;
    private String displayName;
    private String avatarUrl;
    private LocalDate birthDate;
    private String gender;
    private String cpf;
    private String preferredLanguage;
    private String preferredCurrency;
    private boolean newsletterSubscribed;
    private Instant acceptedTermsAt;
    private Instant acceptedPrivacyAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    private Profile(
            final ProfileId id, final UserId userId, final String firstName,
            final String lastName, final String displayName, final String avatarUrl,
            final LocalDate birthDate, final String gender, final String cpf,
            final String preferredLanguage, final String preferredCurrency,
            final boolean newsletterSubscribed, final Instant acceptedTermsAt,
            final Instant acceptedPrivacyAt,
            final Instant createdAt, final Instant updatedAt, final Instant deletedAt
    ) {
        super(id);
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.birthDate = birthDate;
        this.gender = gender;
        this.cpf = cpf;
        this.preferredLanguage = preferredLanguage;
        this.preferredCurrency = preferredCurrency;
        this.newsletterSubscribed = newsletterSubscribed;
        this.acceptedTermsAt = acceptedTermsAt;
        this.acceptedPrivacyAt = acceptedPrivacyAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public static Profile create(final UserId userId) {
        final var now = Instant.now();
        return new Profile(
                ProfileId.unique(), userId,
                null, null, null, null,
                null, null, null,
                "pt-BR", "BRL", false,
                null, null,
                now, now, null
        );
    }

    @Override
    public void validate(ValidationHandler handler) {

    }
}
