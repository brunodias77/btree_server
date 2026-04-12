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
 * <p>Garante idempotência no processamento de eventos do Outbox: antes de processar
 * um evento, o job verifica se o seu {@code id} já consta nesta tabela. Se sim,
 * o evento é ignorado — mesmo que o job seja reiniciado após uma falha parcial,
 * ele não reprocessará eventos já consumidos com sucesso.
 *
 * <h3>Por que manter uma tabela separada de eventos processados?</h3>
 * <p>Embora {@code domain_events.processed_at} indique que o evento foi processado,
 * essa coluna pode ser preenchida ao mesmo tempo que o handler ainda está executando
 * em cenários de concorrência. A tabela {@code processed_events} serve como um
 * "recibo imutável" de entrega: o ID é inserido <b>após</b> a entrega bem-sucedida,
 * funcionando como um lock otimista de idempotência.
 */
@Entity
@Table(name = "processed_events", schema = "shared")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventEntity {

    /**
     * Identificador do evento processado.
     * Corresponde ao {@code id} da {@link DomainEventEntity} de origem.
     * PK simples (sem particionamento) para buscas rápidas de idempotência ({@code existsById}).
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Tipo do evento (ex.: "UserCreated", "OrderCancelled").
     * Armazenado para facilitar consultas de observabilidade e auditoria
     * sem precisar fazer JOIN com a tabela {@code domain_events}.
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * Módulo de negócios que gerou o evento (ex.: "users", "orders").
     * Permite contar eventos processados por módulo via
     * {@link ProcessedEventJpaRepository#countByModuleSince}.
     */
    @Column(name = "module", nullable = false, length = 50)
    private String module;

    /**
     * Timestamp de quando o evento foi consumido com sucesso.
     * Preenchido automaticamente pelo callback {@link #onPrePersist()} se
     * não for informado explicitamente pelo builder.
     */
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    /**
     * Callback JPA executado imediatamente antes do {@code INSERT}.
     *
     * <p>Garante que {@code processed_at} nunca seja {@code NULL} no banco:
     * se o builder não definiu o valor explicitamente, o timestamp atual é usado.
     * Isso remove a necessidade de o chamador lembrar de preencher o campo.
     */
    @PrePersist
    void onPrePersist() {
        if (this.processedAt == null) {
            this.processedAt = Instant.now();
        }
    }
}
