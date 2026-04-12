package com.btree.infrastructure.user.entity;

import com.btree.domain.user.entity.NotificationPreference;
import com.btree.domain.user.identifier.NotificationPreferenceId;
import com.btree.domain.user.identifier.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity para a tabela {@code users.notification_preferences}.
 * Relacionamento 1-1 com {@link UserJpaEntity} (user_id UNIQUE).
 */
@Entity
@Table(name = "notification_preferences", schema = "users")
public class NotificationPreferenceJpaEntity {

@Id
@Column(name = "id", nullable = false, updatable = false)
private UUID id;

@OneToOne
@JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true)
private UserJpaEntity user;

@Column(name = "email_enabled", nullable = false)
private boolean emailEnabled;

@Column(name = "push_enabled", nullable = false)
private boolean pushEnabled;

@Column(name = "sms_enabled", nullable = false)
private boolean smsEnabled;

@Column(name = "order_updates", nullable = false)
private boolean orderUpdates;

@Column(name = "promotions", nullable = false)
private boolean promotions;

@Column(name = "price_drops", nullable = false)
private boolean priceDrops;

@Column(name = "back_in_stock", nullable = false)
private boolean backInStock;

@Column(name = "newsletter", nullable = false)
private boolean newsletter;

@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;

@Column(name = "updated_at", nullable = false)
private Instant updatedAt;

public NotificationPreferenceJpaEntity() {
}

// ── Conversions ──────────────────────────────────────────────────────────

public static NotificationPreferenceJpaEntity from(
        final NotificationPreference domain,
        final UserJpaEntity user
) {
    if (domain == null) return null;
    final var entity = new NotificationPreferenceJpaEntity();
    entity.setId(domain.getId().getValue());
    entity.setUser(user);
    entity.setEmailEnabled(domain.isEmailEnabled());
    entity.setPushEnabled(domain.isPushEnabled());
    entity.setSmsEnabled(domain.isSmsEnabled());
    entity.setOrderUpdates(domain.isOrderUpdates());
    entity.setPromotions(domain.isPromotions());
    entity.setPriceDrops(domain.isPriceDrops());
    entity.setBackInStock(domain.isBackInStock());
    entity.setNewsletter(domain.isNewsletter());
    entity.setCreatedAt(domain.getCreatedAt());
    entity.setUpdatedAt(domain.getUpdatedAt());
    return entity;
}

public NotificationPreference toAggregate() {
    return NotificationPreference.with(
            NotificationPreferenceId.from(this.id),
            UserId.from(this.user),
            this.emailEnabled,
            this.pushEnabled,
            this.smsEnabled,
            this.orderUpdates,
            this.promotions,
            this.priceDrops,
            this.backInStock,
            this.newsletter,
            this.createdAt,
            this.updatedAt
    );
}

/** Atualiza campos mutáveis preservando {@code id}, {@code user} e versão JPA. */
public void updateFrom(final NotificationPreference domain) {
    this.emailEnabled = domain.isEmailEnabled();
    this.pushEnabled = domain.isPushEnabled();
    this.smsEnabled = domain.isSmsEnabled();
    this.orderUpdates = domain.isOrderUpdates();
    this.promotions = domain.isPromotions();
    this.priceDrops = domain.isPriceDrops();
    this.backInStock = domain.isBackInStock();
    this.newsletter = domain.isNewsletter();
    this.updatedAt = domain.getUpdatedAt();
}

// ── Getters & Setters ────────────────────────────────────────────────────

public UUID getId() { return id; }
public void setId(final UUID id) { this.id = id; }

public UserJpaEntity getUser() { return user; }
public void setUser(final UserJpaEntity user) { this.user = user; }

public boolean isEmailEnabled() { return emailEnabled; }
public void setEmailEnabled(final boolean emailEnabled) { this.emailEnabled = emailEnabled; }

public boolean isPushEnabled() { return pushEnabled; }
public void setPushEnabled(final boolean pushEnabled) { this.pushEnabled = pushEnabled; }

public boolean isSmsEnabled() { return smsEnabled; }
public void setSmsEnabled(final boolean smsEnabled) { this.smsEnabled = smsEnabled; }

public boolean isOrderUpdates() { return orderUpdates; }
public void setOrderUpdates(final boolean orderUpdates) { this.orderUpdates = orderUpdates; }

public boolean isPromotions() { return promotions; }
public void setPromotions(final boolean promotions) { this.promotions = promotions; }

public boolean isPriceDrops() { return priceDrops; }
public void setPriceDrops(final boolean priceDrops) { this.priceDrops = priceDrops; }

public boolean isBackInStock() { return backInStock; }
public void setBackInStock(final boolean backInStock) { this.backInStock = backInStock; }

public boolean isNewsletter() { return newsletter; }
public void setNewsletter(final boolean newsletter) { this.newsletter = newsletter; }

public Instant getCreatedAt() { return createdAt; }
public void setCreatedAt(final Instant createdAt) { this.createdAt = createdAt; }

public Instant getUpdatedAt() { return updatedAt; }
public void setUpdatedAt(final Instant updatedAt) { this.updatedAt = updatedAt; }
}
