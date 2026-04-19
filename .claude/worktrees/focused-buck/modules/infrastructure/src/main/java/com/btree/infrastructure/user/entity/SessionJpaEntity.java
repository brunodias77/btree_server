package com.btree.infrastructure.user.entity;
import com.btree.domain.user.entity.Session;
import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions", schema = "users")
public class SessionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "refresh_token_hash", nullable = false, length = 256)
    private String refreshTokenHash;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    public SessionJpaEntity() {}

    private SessionJpaEntity(
            final UUID id,
            final UUID userId,
            final String refreshTokenHash,
            final String ipAddress,
            final String userAgent,
            final Instant expiresAt,
            final boolean revoked,
            final Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.createdAt = createdAt;
    }

    public static SessionJpaEntity from(final Session session) {
        final var deviceInfo = session.getDeviceInfo();
        return new SessionJpaEntity(
                session.getId().getValue(),
                session.getUserId().getValue(),
                session.getRefreshTokenHash(),
                deviceInfo != null ? deviceInfo.getIpAddress() : null,
                deviceInfo != null ? deviceInfo.getUserAgent() : null,
                session.getExpiresAt(),
                session.isRevoked(),
                session.getCreatedAt()
        );
    }

    public Session toAggregate() {
        return Session.with(
                SessionId.from(this.id),
                UserId.from(this.userId),
                this.refreshTokenHash,
                DeviceInfo.of(this.ipAddress, this.userAgent),
                this.expiresAt,
                this.revoked,
                this.createdAt,
                this.createdAt,
                this.version
        );
    }

    public void updateFrom(final Session session) {
        this.revoked = session.isRevoked();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getCreatedAt() { return createdAt; }
    public int getVersion() { return version; }
}
