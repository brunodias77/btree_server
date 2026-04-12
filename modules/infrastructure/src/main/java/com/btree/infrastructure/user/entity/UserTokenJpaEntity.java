package com.btree.infrastructure.user.entity;

import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserTokenId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_tokens", schema = "users")
public class UserTokenJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_type", nullable = false, length = 50)
    private String tokenType;

    @Column(name = "token_hash", nullable = false, length = 256)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserTokenJpaEntity() {}

    private UserTokenJpaEntity(
            final UUID id,
            final UUID userId,
            final String tokenType,
            final String tokenHash,
            final Instant expiresAt,
            final Instant usedAt,
            final Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.tokenType = tokenType;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
    }

    public static UserTokenJpaEntity from(final UserToken token) {
        return new UserTokenJpaEntity(
                token.getId().getValue(),
                token.getUserId().getValue(),
                token.getTokenType(),
                token.getTokenHash(),
                token.getExpiresAt(),
                token.getUsedAt(),
                token.getCreatedAt()
        );
    }

    public UserToken toAggregate() {
        return UserToken.with(
                UserTokenId.from(this.id),
                UserId.from(this.userId),
                this.tokenType,
                this.tokenHash,
                this.expiresAt,
                this.usedAt,
                this.createdAt
        );
    }

    public void updateFrom(final UserToken token) {
        this.usedAt = token.getUsedAt();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenType() { return tokenType; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
