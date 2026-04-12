package com.btree.domain.user.entity;

import com.btree.domain.user.error.UserNotificationError;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.identifier.UserNotificationId;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity — maps to {@code users.notifications} table.
 * Named UserNotification to avoid conflict with {@code com.btree.shared.validations.Notification}.
 */
public class UserNotification extends Entity<UserNotificationId> {

    private UserId userId;
    private String title;
    private String message;
    private String notificationType;
    private String referenceType;
    private UUID referenceId;
    private String actionUrl;
    private Instant readAt;
    private Instant createdAt;

    private UserNotification(
            final UserNotificationId id, final UserId userId, final String title,
            final String message, final String notificationType,
            final String referenceType, final UUID referenceId,
            final String actionUrl, final Instant readAt, final Instant createdAt
    ) {
        super(id);
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.notificationType = notificationType;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.actionUrl = actionUrl;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    public static UserNotification create(
            final UserId userId, final String title,
            final String message, final String notificationType
    ) {
        return new UserNotification(
                UserNotificationId.unique(), userId, title, message,
                notificationType, null, null, null, null, Instant.now()
        );
    }

    public static UserNotification with(
            final UserNotificationId id, final UserId userId, final String title,
            final String message, final String notificationType,
            final String referenceType, final UUID referenceId,
            final String actionUrl, final Instant readAt, final Instant createdAt
    ) {
        return new UserNotification(
                id, userId, title, message, notificationType,
                referenceType, referenceId, actionUrl, readAt, createdAt
        );
    }

    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    @Override
    public void validate(final ValidationHandler handler) {
        if (title == null || title.isBlank()) {
            handler.append(UserNotificationError.TITLE_EMPTY);
        }
        if (message == null || message.isBlank()) {
            handler.append(UserNotificationError.MESSAGE_EMPTY);
        }
        if (notificationType == null || notificationType.isBlank()) {
            handler.append(UserNotificationError.NOTIFICATION_TYPE_EMPTY);
        }
    }

    // ── Getters ──────────────────────────────────────────────

    public UserId getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getNotificationType() { return notificationType; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public String getActionUrl() { return actionUrl; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }




}
