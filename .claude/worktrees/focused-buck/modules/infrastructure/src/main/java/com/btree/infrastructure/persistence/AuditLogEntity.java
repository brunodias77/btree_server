package com.btree.infrastructure.persistence;

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
 * JPA Entity para a tabela {@code shared.audit_logs}.
 *
 * <p>A tabela é particionada por {@code created_at} (RANGE trimestral),
 * exigindo PK composta {@code (id, created_at)} — mapeada via {@link IdClass}.
 *
 * <p>Os campos {@code old_values} e {@code new_values} são armazenados
 * como {@code jsonb} e representados como {@code String} JSON na aplicação.
 *
 * <p>Registros são imutáveis por natureza — não existem operações de update.
 */
@Entity
@Table(name = "audit_logs", schema = "shared")
@IdClass(AuditLogEntity.AuditLogEntityId.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /** Ação realizada: CREATE, UPDATE, DELETE, etc. */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private String oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private String newValues;

    /** Null quando a ação foi disparada por um processo de sistema. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    // ── Composite PK ─────────────────────────────────────────

    public static class AuditLogEntityId implements Serializable {

        private UUID id;
        private Instant createdAt;

        public AuditLogEntityId() {}

        public AuditLogEntityId(final UUID id, final Instant createdAt) {
            this.id = id;
            this.createdAt = createdAt;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof AuditLogEntityId that)) return false;
            return Objects.equals(id, that.id) && Objects.equals(createdAt, that.createdAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, createdAt);
        }
    }
}

