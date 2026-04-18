package com.btree.infrastructure.user.entity;

import com.btree.domain.user.entity.UserSocialLogin;
import com.btree.domain.user.identifier.UserSocialLoginId;
import com.btree.domain.user.identifier.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "social_logins", schema = "users")
public class UserSocialLoginJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 256)
    private String providerUserId;

    @Column(name = "provider_display_name", length = 256)
    private String providerDisplayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserSocialLoginJpaEntity() {}

    private UserSocialLoginJpaEntity(
            final UUID id,
            final UUID userId,
            final String provider,
            final String providerUserId,
            final String providerDisplayName,
            final Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerDisplayName = providerDisplayName;
        this.createdAt = createdAt;
    }

    public static UserSocialLoginJpaEntity from(final UserSocialLogin domain) {
        return new UserSocialLoginJpaEntity(
                domain.getId().getValue(),
                domain.getUserId().getValue(),
                domain.getProvider(),
                domain.getProviderUserId(),
                domain.getProviderDisplayName(),
                domain.getCreatedAt()
        );
    }

    public UserSocialLogin toAggregate() {
        return UserSocialLogin.with(
                UserSocialLoginId.from(this.id),
                UserId.from(this.userId),
                this.provider,
                this.providerUserId,
                this.providerDisplayName,
                this.createdAt
        );
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public String getProviderDisplayName() { return providerDisplayName; }
    public Instant getCreatedAt() { return createdAt; }
}
