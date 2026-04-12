package com.btree.infrastructure.user.entity;

import com.btree.domain.user.entity.LoginHistory;
import com.btree.domain.user.identifier.LoginHistoryId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "login_history", schema = "users")
@IdClass(LoginHistoryJpaEntity.LoginHistoryPk.class)
public class LoginHistoryJpaEntity {

    @Id
    private UUID id;

    @Id
    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    public LoginHistoryJpaEntity() {}

    private LoginHistoryJpaEntity(
            final UUID id,
            final UUID userId,
            final String ipAddress,
            final String userAgent,
            final boolean success,
            final String failureReason,
            final Instant attemptedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.success = success;
        this.failureReason = failureReason;
        this.attemptedAt = attemptedAt;
    }

    public static LoginHistoryJpaEntity from(final LoginHistory loginHistory) {
        final var deviceInfo = loginHistory.getDeviceInfo();
        return new LoginHistoryJpaEntity(
                loginHistory.getId().getValue(),
                loginHistory.getUserId() != null ? loginHistory.getUserId().getValue() : null,
                deviceInfo != null ? deviceInfo.getIpAddress() : null,
                deviceInfo != null ? deviceInfo.getUserAgent() : null,
                loginHistory.isSuccess(),
                loginHistory.getFailureReason(),
                loginHistory.getAttemptedAt()
        );
    }

    public LoginHistory toAggregate() {
        return LoginHistory.with(
                LoginHistoryId.from(this.id),
                this.userId != null ? UserId.from(this.userId) : null,
                DeviceInfo.of(this.ipAddress, this.userAgent),
                this.success,
                this.failureReason,
                this.attemptedAt
        );
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
    public Instant getAttemptedAt() { return attemptedAt; }

    /**
     * Chave composta para a tabela particionada {@code users.login_history}.
     */
    public static class LoginHistoryPk implements Serializable {
        private UUID id;
        private Instant attemptedAt;

        public LoginHistoryPk() {}

        public LoginHistoryPk(final UUID id, final Instant attemptedAt) {
            this.id = id;
            this.attemptedAt = attemptedAt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LoginHistoryPk that)) return false;
            return Objects.equals(id, that.id) && Objects.equals(attemptedAt, that.attemptedAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, attemptedAt);
        }
    }
}

