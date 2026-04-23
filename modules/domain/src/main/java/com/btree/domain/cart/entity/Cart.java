package com.btree.domain.cart.entity;

import com.btree.domain.cart.error.CartError;
import com.btree.domain.cart.error.CartItemError;
import com.btree.domain.cart.event.*;
import com.btree.domain.cart.identifier.CartId;
import com.btree.domain.cart.validator.CartValidator;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.domain.DomainException;
import com.btree.shared.enums.CartStatus;
import com.btree.shared.enums.ShippingMethod;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate Root — maps to {@code cart.carts}.
 *
 * <p>Owns: {@link CartItem} (list).
 * Optimistic locking via {@code version}. Supports both authenticated and guest carts:
 * <ul>
 *   <li>Authenticated: {@code userId} set, {@code sessionId} may be null</li>
 *   <li>Guest: {@code sessionId} set, {@code userId} null (associated after login)</li>
 * </ul>
 *
 * <p>Status lifecycle:
 * <pre>
 *   ACTIVE → CONVERTED  (checkout concluído — PlaceOrder)
 *   ACTIVE → ABANDONED  (inatividade — ExpireAbandonedCarts job)
 *   ACTIVE → EXPIRED    (expires_at atingido — guest cart)
 * </pre>
 */
public class Cart extends AggregateRoot<CartId> {

    private UUID userId;
    private String sessionId;
    private CartStatus status;
    private String couponCode;
    private ShippingMethod shippingMethod;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private final List<CartItem> items = new ArrayList<>();

    private Cart(
            final CartId id,
            final UUID userId,
            final String sessionId,
            final CartStatus status,
            final String couponCode,
            final ShippingMethod shippingMethod,
            final String notes,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant expiresAt,
            final int version
    ) {
        super(id, version);
        this.userId = userId;
        this.sessionId = sessionId;
        this.status = status;
        this.couponCode = couponCode;
        this.shippingMethod = shippingMethod;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.expiresAt = expiresAt;
    }

    // ── Factories ─────────────────────────────────────────────

    /**
     * Cria um carrinho para usuário autenticado.
     */
    public static Cart createForUser(final UUID userId) {
        final var now = Instant.now();
        final var cart = new Cart(
                CartId.unique(),
                userId, null,
                CartStatus.ACTIVE,
                null, null, null,
                now, now, null, 0
        );
        cart.validate(Notification.create());
        cart.registerEvent(new CartCreatedEvent(
                cart.getId().getValue().toString(), userId, null
        ));
        return cart;
    }

    /**
     * Cria um carrinho guest (não autenticado) identificado por sessionId.
     *
     * @param sessionId  identificador da sessão anônima (cookie/JWT anônimo)
     * @param expiresAt  quando o carrinho guest deve expirar
     */
    public static Cart createForGuest(final String sessionId, final Instant expiresAt) {
        final var now = Instant.now();
        final var cart = new Cart(
                CartId.unique(),
                null, sessionId,
                CartStatus.ACTIVE,
                null, null, null,
                now, now, expiresAt, 0
        );
        cart.validate(Notification.create());
        cart.registerEvent(new CartCreatedEvent(
                cart.getId().getValue().toString(), null, sessionId
        ));
        return cart;
    }

    /**
     * Reconstitui um Cart a partir do banco (hydration).
     */
    public static Cart with(
            final CartId id,
            final UUID userId,
            final String sessionId,
            final CartStatus status,
            final String couponCode,
            final ShippingMethod shippingMethod,
            final String notes,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant expiresAt,
            final int version,
            final List<CartItem> items
    ) {
        final var cart = new Cart(
                id, userId, sessionId, status, couponCode,
                shippingMethod, notes, createdAt, updatedAt, expiresAt, version
        );
        if (items != null) {
            cart.items.addAll(items);
        }
        return cart;
    }

    // ── Domain Behaviors — Items ──────────────────────────────

    /**
     * Adiciona um produto ao carrinho. Se já existir, incrementa a quantidade.
     */
    public void addItem(final UUID productId, final int quantity, final BigDecimal unitPrice) {
        assertActive();
        final var existing = findItemByProduct(productId);
        if (existing.isPresent()) {
            existing.get().updateQuantity(existing.get().getQuantity() + quantity);
        } else {
            items.add(CartItem.create(getId(), productId, quantity, unitPrice));
        }
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new ItemAddedToCartEvent(
                getId().getValue().toString(), productId, quantity, unitPrice
        ));
    }

    /**
     * Remove um produto do carrinho. Lança exceção se o produto não estiver no carrinho.
     */
    public void removeItem(final UUID productId) {
        assertActive();
        final var item = findItemByProduct(productId)
                .orElseThrow(() -> DomainException.with(CartItemError.ITEM_NOT_FOUND));
        items.remove(item);
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new ItemRemovedFromCartEvent(getId().getValue().toString(), productId));
    }

    /**
     * Atualiza a quantidade de um item. Se quantity == 0, remove o item.
     */
    public void updateItemQuantity(final UUID productId, final int quantity) {
        assertActive();
        if (quantity == 0) {
            removeItem(productId);
            return;
        }
        final var item = findItemByProduct(productId)
                .orElseThrow(() -> DomainException.with(CartItemError.ITEM_NOT_FOUND));
        item.updateQuantity(quantity);
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    /**
     * Atualiza o preço de um item (ex: preço mudou desde que foi adicionado).
     */
    public void refreshItemPrice(final UUID productId, final BigDecimal newPrice) {
        assertActive();
        findItemByProduct(productId).ifPresent(item -> item.updatePrice(newPrice));
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    // ── Domain Behaviors — Coupon ─────────────────────────────

    public void applyCoupon(final String couponCode) {
        assertActive();
        if (this.couponCode != null) {
            throw DomainException.with(CartError.COUPON_ALREADY_APPLIED);
        }
        this.couponCode = couponCode;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new CartCouponAppliedEvent(getId().getValue().toString(), couponCode));
    }

    public void removeCoupon() {
        assertActive();
        if (this.couponCode == null) {
            throw DomainException.with(CartError.NO_COUPON_APPLIED);
        }
        final var removed = this.couponCode;
        this.couponCode = null;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new CartCouponRemovedEvent(getId().getValue().toString(), removed));
    }

    // ── Domain Behaviors — Shipping / Notes ───────────────────

    public void selectShipping(final ShippingMethod method) {
        assertActive();
        if (method == null) {
            throw DomainException.with(CartError.SHIPPING_METHOD_NULL);
        }
        this.shippingMethod = method;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public void addNotes(final String notes) {
        assertActive();
        this.notes = notes;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    // ── Domain Behaviors — Status transitions ─────────────────

    public void associateUser(final UUID userId) {
        assertActive();
        this.userId = userId;
        this.updatedAt = Instant.now();
        incrementVersion();
    }

    public void markAsConverted() {
        assertActive();
        this.status = CartStatus.CONVERTED;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new CartConvertedEvent(getId().getValue().toString(), userId));
    }

    public void markAsAbandoned() {
        assertActive();
        this.status = CartStatus.ABANDONED;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new CartAbandonedEvent(getId().getValue().toString(), userId));
    }

    public void markAsExpired() {
        assertActive();
        this.status = CartStatus.EXPIRED;
        this.updatedAt = Instant.now();
        incrementVersion();
        registerEvent(new CartExpiredEvent(getId().getValue().toString(), userId));
    }

    // ── Computed ─────────────────────────────────────────────

    public BigDecimal subtotal() {
        return items.stream()
                .map(CartItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int totalItems() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean isActive() {
        return CartStatus.ACTIVE.equals(this.status);
    }

    // ── Validation ───────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new CartValidator(this, handler).validate();
    }

    // ── Helpers ───────────────────────────────────────────────

    private void assertActive() {
        if (!isActive()) {
            throw DomainException.with(CartError.CART_NOT_ACTIVE);
        }
    }

    private Optional<CartItem> findItemByProduct(final UUID productId) {
        return items.stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getUserId()             { return userId; }
    public String getSessionId()        { return sessionId; }
    public CartStatus getStatus()       { return status; }
    public String getCouponCode()       { return couponCode; }
    public ShippingMethod getShippingMethod() { return shippingMethod; }
    public String getNotes()            { return notes; }
    public Instant getCreatedAt()       { return createdAt; }
    public Instant getUpdatedAt()       { return updatedAt; }
    public Instant getExpiresAt()       { return expiresAt; }
    public List<CartItem> getItems()    { return Collections.unmodifiableList(items); }
}
