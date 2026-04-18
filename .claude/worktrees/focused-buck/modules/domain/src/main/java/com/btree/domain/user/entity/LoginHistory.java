package com.btree.domain.user.entity;


import com.btree.domain.user.identifier.LoginHistoryId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.valueobject.DeviceInfo;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.Error;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;

public class LoginHistory extends Entity<LoginHistoryId> {

    private final UserId userId;
    private final DeviceInfo deviceInfo;
    private final boolean success;
    private final String failureReason;
    private final Instant attemptedAt;

    private LoginHistory(
            final LoginHistoryId id,
            final UserId userId,
            final DeviceInfo deviceInfo,
            final boolean success,
            final String failureReason,
            final Instant attemptedAt
    ) {
        super(id);
        this.userId = userId;
        this.deviceInfo = deviceInfo;
        this.success = success;
        this.failureReason = failureReason;
        this.attemptedAt = attemptedAt;
    }

    public static LoginHistory recordSuccess(
            final UserId userId,
            final DeviceInfo deviceInfo,
            final Notification notification
    ) {
        if (userId == null) notification.append(new Error("'userId' não pode ser nulo"));
        if (notification.hasError()) {
            throw com.btree.shared.domain.DomainException.with(notification.getErrors());
        }

        return new LoginHistory(LoginHistoryId.unique(), userId, deviceInfo, true, null, Instant.now());
    }

    /**
     * Registra uma tentativa de login malsucedida.
     *
     * @param userId        ID do usuário tentando fazer login; pode ser {@code null}
     *                      quando o usuário não existe (ex: e-mail desconhecido), pois
     *                      nesse caso não há UserId para associar ao registro.
     * @param deviceInfo    informações do dispositivo da tentativa
     * @param failureReason descrição do motivo da falha
     * @param notification  handler de validação (não utilizado — assinatura mantida por consistência)
     */
    public static LoginHistory recordFailure(
            final UserId userId,
            final DeviceInfo deviceInfo,
            final String failureReason,
            final Notification notification
    ) {
        return new LoginHistory(LoginHistoryId.unique(), userId, deviceInfo, false, failureReason, Instant.now());
    }

    public static LoginHistory with(
            final LoginHistoryId id,
            final UserId userId,
            final DeviceInfo deviceInfo,
            final boolean success,
            final String failureReason,
            final Instant attemptedAt
    ) {
        return new LoginHistory(id, userId, deviceInfo, success, failureReason, attemptedAt);
    }

    @Override
    public void validate(ValidationHandler handler) {
        // No complex validations
    }

    public UserId getUserId() { return userId; }
    public DeviceInfo getDeviceInfo() { return deviceInfo; }
    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
