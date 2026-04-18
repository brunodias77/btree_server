package com.btree.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for Aggregate Roots — the consistency boundary in DDD.
 * <p>
 * Extends Entity with:
 * <ul>
 *     <li><b>Domain Events</b> — collected during mutations, dispatched after persistence
 *         (maps to {@code shared.domain_events} outbox table)</li>
 *     <li><b>Optimistic locking</b> via {@code version} — maps to the {@code version INT NOT NULL DEFAULT 1}
 *         column present in products, profiles, carts, orders, payments, coupons</li>
 * </ul>
 * <p>
 * Aggregates in this system (derived from the schema):
 * <ul>
 *     <li>{@code users.users} → User aggregate (profiles, addresses, sessions, tokens, social logins)</li>
 *     <li>{@code catalog.products} → Product aggregate (images, stock_movements, stock_reservations)</li>
 *     <li>{@code catalog.categories} → Category aggregate</li>
 *     <li>{@code cart.carts} → Cart aggregate (items, activity_log)</li>
 *     <li>{@code orders.orders} → Order aggregate (items, status_history, tracking_events, invoices, refunds)</li>
 *     <li>{@code payments.payments} → Payment aggregate (transactions, refunds, chargebacks)</li>
 *     <li>{@code coupons.coupons} → Coupon aggregate (eligible_*, usages, reservations)</li>
 * </ul>
 *
 * @param <ID> the type of the aggregate identifier
 */
public abstract class AggregateRoot<ID extends Identifier> extends Entity<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();
    private int version;

    protected AggregateRoot(final ID id) {
        this(id, 0);
    }

    protected AggregateRoot(final ID id, final int version) {
        super(id);
        this.version = version;
    }

    // ── Domain Events ────────────────────────────────────────

    /**
     * Register a domain event to be dispatched after the aggregate is persisted.
     * Maps to the outbox pattern via {@code shared.domain_events} table.
     */
    protected void registerEvent(final DomainEvent event) {
        if (event != null) {
            this.domainEvents.add(event);
        }
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    // ── Versioning (Optimistic Locking) ──────────────────────

    public int getVersion() {
        return version;
    }

    protected void incrementVersion() {
        this.version++;
    }
}

