package com.btree.domain.user.entity;

import com.btree.domain.user.event.UserCreatedEvent;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.validator.UserValidator;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public class User extends AggregateRoot<UserId> {

    private String username;
    private String email;
    private boolean emailVerified;
    private String passwordHash;
    private String phoneNumber;
    private boolean phoneNumberVerified;
    private boolean twoFactorEnabled;
    private String twoFactorSecret;
    private boolean accountLocked;
    private Instant lockExpiresAt;
    private int accessFailedCount;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
    private Profile profile;
    private NotificationPreference notificationPreference;
    private final Set<String> roles = new HashSet<>();

    private User(
            final UserId id,
            final String username,
            final String email,
            final boolean emailVerified,
            final String passwordHash,
            final String phoneNumber,
            final boolean phoneNumberVerified,
            final boolean twoFactorEnabled,
            final String twoFactorSecret,
            final boolean accountLocked,
            final Instant lockExpiresAt,
            final int accessFailedCount,
            final boolean enabled,
            final Instant createdAt,
            final Instant updatedAt,
            final int version
    ) {
        super(id, version);
        this.username = username;
        this.email = email;
        this.emailVerified = emailVerified;
        this.passwordHash = passwordHash;
        this.phoneNumber = phoneNumber;
        this.phoneNumberVerified = phoneNumberVerified;
        this.twoFactorEnabled = twoFactorEnabled;
        this.twoFactorSecret = twoFactorSecret;
        this.accountLocked = accountLocked;
        this.lockExpiresAt = lockExpiresAt;
        this.accessFailedCount = accessFailedCount;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User create(final String username, final String email, final String passwordHash, final Notification notification){
        final var now = Instant.now();
        final var user = new User(
                UserId.unique(),
                username,
                email,
                false,
                passwordHash,
                null,
                false,
                false,
                null,
                false,
                null,
                0,
                true,
                now,
                now,
                0
        );

        user.profile = Profile.create(user.getId());
        user.notificationPreference = NotificationPreference.create(user.getId());
        user.validate(notification);
        if(notification.hasError()){
            throw DomainException.with(notification.getErrors());
        }

        user.registerEvent(new UserCreatedEvent(user.getId().getValue().toString(), user.getUsername(), user.getEmail()));
        return user;
    }

    @Override
    public void validate(ValidationHandler handler) {
        new UserValidator(this, handler).validate();
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public boolean isPhoneNumberVerified() {
        return phoneNumberVerified;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public Instant getLockExpiresAt() {
        return lockExpiresAt;
    }

    public int getAccessFailedCount() {
        return accessFailedCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Profile getProfile() {
        return profile;
    }

    public NotificationPreference getNotificationPreference() {
        return notificationPreference;
    }

    public Set<String> getRoles() {
        return roles;
    }
}
