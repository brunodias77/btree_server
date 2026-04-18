package com.btree.domain.user.entity;

import com.btree.domain.user.identifier.NotificationPreferenceId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;

/**
 * Entity — maps to {@code users.notification_preferences} table.
 * One-to-one relationship with User (user_id UNIQUE).
 */
public class NotificationPreference extends Entity<NotificationPreferenceId> {

    private UserId userId;
    private boolean emailEnabled;
    private boolean pushEnabled;
    private boolean smsEnabled;
    private boolean orderUpdates;
    private boolean promotions;
    private boolean priceDrops;
    private boolean backInStock;
    private boolean newsletter;
    private Instant createdAt;
    private Instant updatedAt;

    private NotificationPreference(
            final NotificationPreferenceId id, final UserId userId,
            final boolean emailEnabled, final boolean pushEnabled, final boolean smsEnabled,
            final boolean orderUpdates, final boolean promotions, final boolean priceDrops,
            final boolean backInStock, final boolean newsletter,
            final Instant createdAt, final Instant updatedAt
    ) {
        super(id);
        this.userId = userId;
        this.emailEnabled = emailEnabled;
        this.pushEnabled = pushEnabled;
        this.smsEnabled = smsEnabled;
        this.orderUpdates = orderUpdates;
        this.promotions = promotions;
        this.priceDrops = priceDrops;
        this.backInStock = backInStock;
        this.newsletter = newsletter;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory: creates with schema defaults.
     */
    public static NotificationPreference create(final UserId userId) {
        final var now = Instant.now();
        return new NotificationPreference(
                NotificationPreferenceId.unique(), userId,
                true, true, false,
                true, true, true, true, false,
                now, now
        );
    }

    public static NotificationPreference with(
            final NotificationPreferenceId id, final UserId userId,
            final boolean emailEnabled, final boolean pushEnabled, final boolean smsEnabled,
            final boolean orderUpdates, final boolean promotions, final boolean priceDrops,
            final boolean backInStock, final boolean newsletter,
            final Instant createdAt, final Instant updatedAt
    ) {
        return new NotificationPreference(
                id, userId, emailEnabled, pushEnabled, smsEnabled,
                orderUpdates, promotions, priceDrops, backInStock, newsletter,
                createdAt, updatedAt
        );
    }

    public void update(
            final boolean emailEnabled, final boolean pushEnabled, final boolean smsEnabled,
            final boolean orderUpdates, final boolean promotions, final boolean priceDrops,
            final boolean backInStock, final boolean newsletter
    ) {
        this.emailEnabled = emailEnabled;
        this.pushEnabled = pushEnabled;
        this.smsEnabled = smsEnabled;
        this.orderUpdates = orderUpdates;
        this.promotions = promotions;
        this.priceDrops = priceDrops;
        this.backInStock = backInStock;
        this.newsletter = newsletter;
        this.updatedAt = Instant.now();
    }

    @Override
    public void validate(final ValidationHandler handler) {
        // All fields are booleans with defaults — no invariants to break
    }

    // ── Getters ──────────────────────────────────────────────

    public UserId getUserId() { return userId; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public boolean isPushEnabled() { return pushEnabled; }
    public boolean isSmsEnabled() { return smsEnabled; }
    public boolean isOrderUpdates() { return orderUpdates; }
    public boolean isPromotions() { return promotions; }
    public boolean isPriceDrops() { return priceDrops; }
    public boolean isBackInStock() { return backInStock; }
    public boolean isNewsletter() { return newsletter; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

