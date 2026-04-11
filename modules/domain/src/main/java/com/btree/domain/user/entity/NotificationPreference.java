package com.btree.domain.user.entity;

import com.btree.domain.user.identifier.NotificationPreferenceId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;

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

    @Override
    public void validate(ValidationHandler handler) {

    }
}
