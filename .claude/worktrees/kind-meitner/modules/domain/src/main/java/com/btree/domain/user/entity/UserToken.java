package com.btree.domain.user.entity;

import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.util.Objects;

public class UserToken extends Entity<UserTokenId> {

    private final UserId userId;
    private final String tokenType;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant usedAt;
    private final Instant createdAt;

    private UserToken(
            final UserTokenId id,
            final UserId userId,
            final String tokenType,
            final String tokenHash,
            final Instant expiresAt,
            final Instant usedAt,
            final Instant createdAt
    ) {
        super(id);
        this.userId = Objects.requireNonNull(userId, "'userId' must not be null");
        this.tokenType = Objects.requireNonNull(tokenType, "'tokenType' must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "'tokenHash' must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "'expiresAt' must not be null");
        this.usedAt = usedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "'createdAt' must not be null");
    }

    public static UserToken create(
            final UserId userId,
            final String tokenType,
            final String tokenHash,
            final Instant expiresAt
    ) {
        return new UserToken(UserTokenId.unique(), userId, tokenType, tokenHash, expiresAt, null, Instant.now());
    }

    public static UserToken with(
            final UserTokenId id,
            final UserId userId,
            final String tokenType,
            final String tokenHash,
            final Instant expiresAt,
            final Instant usedAt,
            final Instant createdAt
    ) {
        return new UserToken(id, userId, tokenType, tokenHash, expiresAt, usedAt, createdAt);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    public boolean isUsed() {
        return this.usedAt != null;
    }

    public void markAsUsed() {
        this.usedAt = Instant.now();
    }

    @Override
    public void validate(final ValidationHandler handler) {
        // No complex validations for now
    }

    public UserId getUserId() { return userId; }
    public String getTokenType() { return tokenType; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}