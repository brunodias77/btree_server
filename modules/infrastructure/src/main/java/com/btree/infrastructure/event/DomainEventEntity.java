package com.btree.infrastructure.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity para a tabela {@code shared.domain_events} (Outbox Pattern).
 *
 * <p>A tabela é particionada por {@code created_at} (RANGE trimestral),
 * exigindo PK composta {@code (id, created_at)} — mapeada via {@link IdClass}.
 *
 * <p>Campos JSON ({@code payload}) são armazenados como {@code jsonb} no PostgreSQL
 * e serializados/desserializados como {@code String} na aplicação.
 */
@Entity
@Table(name = "domain_events", schema = "shared")
@IdClass(DomainEventEntity.DomainEventEntityId.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    // ── Behaviors ────────────────────────────────────────────

    public void markAsProcessed() {
        this.processedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markAsFailed(final String error) {
        this.errorMessage = error;
        this.retryCount++;
    }

    public boolean isProcessed() {
        return processedAt != null;
    }

    // ── Composite PK ─────────────────────────────────────────

    public static class DomainEventEntityId implements Serializable {

        private UUID id;
        private Instant createdAt;

        public DomainEventEntityId() {}

        public DomainEventEntityId(final UUID id, final Instant createdAt) {
            this.id = id;
            this.createdAt = createdAt;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof DomainEventEntityId that)) return false;
            return Objects.equals(id, that.id) && Objects.equals(createdAt, that.createdAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, createdAt);
        }
    }
}
