package com.btree.domain.user.entity;

import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserSocialLoginId;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.util.Objects;

public class UserSocialLogin extends Entity<UserSocialLoginId> {

    private final UserId userId;
    private final String provider;
    private final String providerUserId;
    private final String providerDisplayName;
    private final Instant createdAt;

    private UserSocialLogin(
            final UserSocialLoginId id,
            final UserId userId,
            final String provider,
            final String providerUserId,
            final String providerDisplayName,
            final Instant createdAt
    ) {
        super(id);
        this.userId = Objects.requireNonNull(userId, "'userId' must not be null");
        this.provider = Objects.requireNonNull(provider, "'provider' must not be null");
        this.providerUserId = Objects.requireNonNull(providerUserId, "'providerUserId' must not be null");
        this.providerDisplayName = providerDisplayName;
        this.createdAt = Objects.requireNonNull(createdAt, "'createdAt' must not be null");
    }

    public static UserSocialLogin create(
            final UserId userId,
            final String provider,
            final String providerUserId,
            final String providerDisplayName
    ) {
        return new UserSocialLogin(
                UserSocialLoginId.unique(),
                userId,
                provider,
                providerUserId,
                providerDisplayName,
                Instant.now()
        );
    }

    public static UserSocialLogin with(
            final UserSocialLoginId id,
            final UserId userId,
            final String provider,
            final String providerUserId,
            final String providerDisplayName,
            final Instant createdAt
    ) {
        return new UserSocialLogin(
                id,
                userId,
                provider,
                providerUserId,
                providerDisplayName,
                createdAt
        );
    }

    @Override
    public void validate(final ValidationHandler handler) {
        // No complex validations needed for now
    }

    public UserId getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public String getProviderDisplayName() { return providerDisplayName; }
    public Instant getCreatedAt() { return createdAt; }
}