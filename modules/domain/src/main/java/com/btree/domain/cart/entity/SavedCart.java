package com.btree.domain.cart.entity;

import com.btree.domain.cart.identifier.SavedCartId;
import com.btree.domain.cart.validator.SavedCartValidator;
import com.btree.domain.cart.value_object.SavedCartItem;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — maps to {@code cart.saved_carts}.
 *
 * <p>Permite ao usuário salvar uma lista de produtos com quantidade para uso futuro.
 * O campo {@code items} é serializado como JSONB na infraestrutura.
 * Sem versão (sem optimistic locking).
 */
public class SavedCart extends AggregateRoot<SavedCartId> {

    private final UUID userId;
    private String name;
    private final List<SavedCartItem> items;
    private Instant createdAt;
    private Instant updatedAt;

    private SavedCart(
            final SavedCartId id,
            final UUID userId,
            final String name,
            final List<SavedCartItem> items,
            final Instant createdAt,
            final Instant updatedAt
    ) {
        super(id, 0);
        this.userId = userId;
        this.name = name;
        this.items = new ArrayList<>(items != null ? items : List.of());
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SavedCart create(
            final UUID userId,
            final String name,
            final List<SavedCartItem> items
    ) {
        final var now = Instant.now();
        final var savedCart = new SavedCart(
                SavedCartId.unique(), userId, name, items, now, now
        );
        final var notification = Notification.create();
        savedCart.validate(notification);
        if (notification.hasError()) {
            throw DomainException.with(notification.getErrors());
        }
        return savedCart;
    }

    public static SavedCart with(
            final SavedCartId id,
            final UUID userId,
            final String name,
            final List<SavedCartItem> items,
            final Instant createdAt,
            final Instant updatedAt
    ) {
        return new SavedCart(id, userId, name, items, createdAt, updatedAt);
    }

    // ── Domain Behaviors ─────────────────────────────────────

    public void rename(final String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new SavedCartValidator(this, handler).validate();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getUserId()              { return userId; }
    public String getName()              { return name; }
    public List<SavedCartItem> getItems(){ return Collections.unmodifiableList(items); }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
}
