package com.btree.infrastructure.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity para a tabela {@code shared.processed_events}.
 *
 * <p>Garante idempotência no processamento de eventos: antes de processar
 * um evento do outbox, verifica-se se o seu {@code id} já consta nesta tabela.
 * Se sim, o evento é ignorado (já foi processado com sucesso anteriormente).
 */
@Entity
@Table(name = "processed_events", schema = "shared")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @PrePersist
    void onPrePersist() {
        if (this.processedAt == null) {
            this.processedAt = Instant.now();
        }
    }
}
