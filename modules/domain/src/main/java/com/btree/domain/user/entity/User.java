package com.btree.domain.user.entity;

import com.btree.domain.user.event.*;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.validator.UserValidator;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;


/**
 * Aggregate Root — maps to {@code users.users} table.
 *
 * <p>Owns: Profile, Addresses, Sessions, Tokens, SocialLogins,
 * LoginHistory, Notifications, NotificationPreference.
 */
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
    private final java.util.Set<String> roles = new java.util.HashSet<>();
    private boolean requiresPassword = true;

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

    /**
     * Factory: creates a new User (register).
     */
    public static User create(
            final String username,
            final String email,
            final String passwordHash,
            final Notification notification
    ) {
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
        user.requiresPassword = true;

        user.validate(notification);
        if (notification.hasError()) {
            throw com.btree.shared.domain.DomainException.with(notification.getErrors());
        }

        user.registerEvent(new UserCreatedEvent(
                user.getId().getValue().toString(),
                user.getUsername(),
                user.getEmail()
        ));

        return user;
    }

    /**
     * Factory: creates a new User from a Social Provider (no password).
     */
    public static User createFromSocial(
            final String username,
            final String email,
            final Notification notification
    ) {
        final var now = Instant.now();
        final var user = new User(
                UserId.unique(),
                username,
                email,
                true, // email verified by social provider
                null, // no password hash
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
        user.requiresPassword = false;

        user.validate(notification);
        if (notification.hasError()) {
            throw com.btree.shared.domain.DomainException.with(notification.getErrors());
        }

        return user;
    }
    public static User with(
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
            final int version,
            final Profile profile,
            final NotificationPreference notificationPreference
    ) {
        final var user = new User(
                id, username, email, emailVerified, passwordHash,
                phoneNumber, phoneNumberVerified, twoFactorEnabled,
                twoFactorSecret, accountLocked, lockExpiresAt, accessFailedCount,
                enabled, createdAt, updatedAt, version
        );
        user.profile = profile;
        user.notificationPreference = notificationPreference;
        return user;
    }

    // ── Domain Behaviors ─────────────────────────────────────

    public void verifyEmail() {
        this.emailVerified = true;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new UserEmailVerifiedEvent(getId().getValue().toString()));
    }

    public void lockAccount(final Instant expiresAt) {
        this.accountLocked = true;
        this.lockExpiresAt = expiresAt;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new UserAccountLockedEvent(getId().getValue().toString(), expiresAt));
    }

    public void unlockAccount() {
        this.accountLocked = false;
        this.lockExpiresAt = null;
        this.accessFailedCount = 0;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new UserAccountUnlockedEvent(getId().getValue().toString()));
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public void enable() {
        this.enabled = true;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public void enableTwoFactor(final String secret) {
        this.twoFactorEnabled = true;
        this.twoFactorSecret = secret;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new UserTwoFactorEnabledEvent(getId().getValue().toString()));
    }

    public void disableTwoFactor() {
        this.twoFactorEnabled = false;
        this.twoFactorSecret = null;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new UserTwoFactorDisabledEvent(getId().getValue().toString()));
    }

    public void incrementAccessFailed() {
        this.accessFailedCount++;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public void resetAccessFailed() {
        this.accessFailedCount = 0;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public void changePassword(final String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new UserPasswordChangedEvent(getId().getValue().toString()));
    }

    public void changeEmail(final String newEmail) {
        this.email = newEmail;
        this.emailVerified = false;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new UserEmailChangedEvent(getId().getValue().toString(), newEmail));
    }

    public void changePhoneNumber(final String newPhoneNumber) {
        this.phoneNumber = newPhoneNumber;
        this.phoneNumberVerified = false;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public void verifyPhoneNumber() {
        this.phoneNumberVerified = true;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public void requestPasswordReset(final String rawToken, final Instant expiresAt) {
        registerEvent(new PasswordResetRequestedEvent(
                getId().getValue().toString(),
                this.email,
                rawToken,
                expiresAt
        ));
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new UserValidator(this, handler).validate();
    }

    // ── Getters ──────────────────────────────────────────────

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getPasswordHash() { return passwordHash; }
    public String getPhoneNumber() { return phoneNumber; }
    public boolean isPhoneNumberVerified() { return phoneNumberVerified; }
    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public String getTwoFactorSecret() { return twoFactorSecret; }
    public boolean isAccountLocked() { return accountLocked; }
    public Instant getLockExpiresAt() { return lockExpiresAt; }
    public int getAccessFailedCount() { return accessFailedCount; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Profile getProfile() { return profile; }
    public NotificationPreference getNotificationPreference() { return notificationPreference; }
    public java.util.Set<String> getRoles() { return java.util.Collections.unmodifiableSet(roles); }
    public boolean isRequiresPassword() { return requiresPassword; }

    public void addRole(String role) {
        if (role != null && !role.isBlank()) {
            this.roles.add(role);
        }
    }
}


