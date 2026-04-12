package com.btree.domain.user.entity;

import com.btree.domain.user.identifier.SessionId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.validator.SessionValidator;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;

public class Session extends AggregateRoot<SessionId> {

    private final UserId userId;
    private final String refreshTokenHash;
    private final DeviceInfo deviceInfo;
    private final Instant expiresAt;
    private boolean revoked;
    private final Instant createdAt;
    private Instant updatedAt;

    private Session(
            final SessionId id,
            final UserId userId,
            final String refreshTokenHash,
            final DeviceInfo deviceInfo,
            final Instant expiresAt,
            final boolean revoked,
            final Instant createdAt,
            final Instant updatedAt,
            final int version
    ) {
        super(id, version);
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.deviceInfo = deviceInfo;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Session create(
            final UserId userId,
            final String refreshTokenHash,
            final DeviceInfo deviceInfo,
            final Instant expiresAt,
            final Notification notification
    ) {
        final var now = Instant.now();
        final var session = new Session(
                SessionId.unique(), userId, refreshTokenHash,
                deviceInfo, expiresAt, false, now, now, 0
        );

        session.validate(notification);
        if (notification.hasError()) {
            throw DomainException.with(notification.getErrors());
        }

        return session;
    }

    public static Session with(
            final SessionId id,
            final UserId userId,
            final String refreshTokenHash,
            final DeviceInfo deviceInfo,
            final Instant expiresAt,
            final boolean revoked,
            final Instant createdAt,
            final Instant updatedAt,
            final int version
    ) {
        return new Session(id, userId, refreshTokenHash, deviceInfo, expiresAt, revoked, createdAt, updatedAt, version);
    }

    public void revoke() {
        this.revoked = true;
        this.updatedAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    public boolean isActive() {
        return !this.revoked && !this.isExpired();
    }

    @Override
    public void validate(final ValidationHandler handler) {
        new SessionValidator(this, handler).validate();
    }

    public UserId getUserId() { return userId; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public DeviceInfo getDeviceInfo() { return deviceInfo; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}