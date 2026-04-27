package com.btree.infrastructure.cart.entity;

import com.btree.domain.cart.entity.SavedCart;
import com.btree.domain.cart.identifier.SavedCartId;
import com.btree.domain.cart.value_object.SavedCartItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity — maps to {@code cart.saved_carts}.
 *
 * <p>The {@code items} field is a JSONB array. It is serialized/deserialized
 * to/from {@code List<SavedCartItem>} using Jackson inside the gateway.
 * The raw JSON string is stored here to keep the JPA entity free of
 * ObjectMapper dependency — conversion happens in {@link com.btree.infrastructure.cart.persistence.SavedCartPostgresGateway}.
 */
@Entity
@Table(name = "saved_carts", schema = "cart")
public class SavedCartJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "items", nullable = false, columnDefinition = "JSONB")
    private String itemsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SavedCartJpaEntity() {}

    public static SavedCartJpaEntity from(final SavedCart savedCart, final ObjectMapper mapper) {
        final var entity = new SavedCartJpaEntity();
        entity.id = savedCart.getId().getValue();
        entity.userId = savedCart.getUserId();
        entity.name = savedCart.getName();
        entity.itemsJson = serializeItems(savedCart.getItems(), mapper);
        entity.createdAt = savedCart.getCreatedAt();
        entity.updatedAt = savedCart.getUpdatedAt();
        return entity;
    }

    public SavedCart toAggregate(final ObjectMapper mapper) {
        return SavedCart.with(
                SavedCartId.from(this.id),
                this.userId,
                this.name,
                deserializeItems(this.itemsJson, mapper),
                this.createdAt,
                this.updatedAt
        );
    }

    public void updateFrom(final SavedCart savedCart, final ObjectMapper mapper) {
        this.name = savedCart.getName();
        this.itemsJson = serializeItems(savedCart.getItems(), mapper);
        this.updatedAt = savedCart.getUpdatedAt();
    }

    // ── JSON helpers ─────────────────────────────────────────

    private static final TypeReference<List<SavedCartItemJson>> LIST_TYPE =
            new TypeReference<>() {};

    private static String serializeItems(final List<SavedCartItem> items, final ObjectMapper mapper) {
        try {
            final var jsonList = items.stream()
                    .map(i -> new SavedCartItemJson(i.getProductId(), i.getQuantity()))
                    .toList();
            return mapper.writeValueAsString(jsonList);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Erro ao serializar itens do carrinho salvo", e);
        }
    }

    private static List<SavedCartItem> deserializeItems(final String json, final ObjectMapper mapper) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, LIST_TYPE).stream()
                    .map(j -> SavedCartItem.of(j.productId(), j.quantity()))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Erro ao desserializar itens do carrinho salvo", e);
        }
    }

    /** Internal DTO for JSON serialization of SavedCartItem. */
    private record SavedCartItemJson(UUID productId, int quantity) {}

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
