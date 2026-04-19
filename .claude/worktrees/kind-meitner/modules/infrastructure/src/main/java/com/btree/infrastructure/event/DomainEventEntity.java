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
 *
 * <h3>Fluxo do Outbox Pattern</h3>
 * <ol>
 *   <li>O aggregate persiste suas mudanças e os domain events nesta tabela
 *       dentro da <b>mesma transação</b> (atomicidade garantida pelo banco).</li>
 *   <li>Um job de polling ({@code ProcessDomainEventsUseCase}) lê os registros
 *       pendentes ({@code processed_at IS NULL}) e os despacha para os handlers.</li>
 *   <li>Após entrega bem-sucedida, o campo {@code processed_at} é preenchido
 *       via {@link #markAsProcessed()}.</li>
 *   <li>Em caso de falha, {@link #markAsFailed(String)} registra o erro e
 *       incrementa {@code retry_count} para controle de reprocessamento.</li>
 * </ol>
 */
@Entity
@Table(name = "domain_events", schema = "shared")
@IdClass(DomainEventEntity.DomainEventEntityId.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEventEntity {

    /** Identificador único do evento. Parte da PK composta junto com {@code createdAt}. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Instante em que o evento ocorreu no domínio.
     * Faz parte da PK composta porque a tabela é particionada por {@code created_at} (RANGE trimestral):
     * o PostgreSQL exige que a coluna de particionamento esteja sempre presente na chave primária.
     */
    @Id
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Módulo de origem do evento (ex: "users", "orders", "catalog").
     * Derivado do {@code aggregateType} pelo {@link OutboxDomainEventPublisher}.
     * Usado para roteamento e monitoramento por área de negócios.
     */
    @Column(name = "module", nullable = false, length = 50)
    private String module;

    /**
     * Nome do tipo do agregate que gerou o evento (ex: "User", "Order").
     * Usado em conjunto com {@code aggregateId} para reconstruir o contexto do evento.
     */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /**
     * Identificador da instância do aggregate que originou o evento.
     * Permite recuperar o histórico de eventos de um aggregate específico via
     * {@link DomainEventJpaRepository#findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc}.
     */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    /**
     * Nome qualificado do tipo do evento (ex: "UserCreated", "OrderCancelled").
     * Utilizado pelo job de processamento para rotear o evento ao handler correto.
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * Payload JSON do evento serializado ({@code jsonb} no PostgreSQL).
     * Contém todos os dados necessários para o handler reconstruir o evento
     * sem precisar consultar o banco de dados principal.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    /**
     * Timestamp de quando o evento foi processado com sucesso.
     * {@code NULL} significa que o evento ainda está pendente de processamento.
     * Preenchido por {@link #markAsProcessed()}.
     */
    @Column(name = "processed_at")
    private Instant processedAt;

    /**
     * Mensagem de erro da última tentativa de processamento, se houver.
     * Limpo por {@link #markAsProcessed()} quando o evento é processado com sucesso.
     */
    @Column(name = "error_message")
    private String errorMessage;

    /**
     * Número de tentativas de reprocessamento já realizadas.
     * Incrementado por {@link #markAsFailed(String)} a cada falha.
     * Usado pelo job de polling para filtrar eventos que excederam o limite de retries.
     */
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    // ── Behaviors ────────────────────────────────────────────

    /**
     * Marca o evento como processado com sucesso.
     * Preenche {@code processed_at} com o instante atual e limpa qualquer
     * mensagem de erro registrada em tentativas anteriores.
     */
    public void markAsProcessed() {
        this.processedAt = Instant.now();
        this.errorMessage = null;
    }

    /**
     * Registra uma falha no processamento do evento.
     * Persiste a mensagem de erro para diagnóstico e incrementa o contador
     * de tentativas ({@code retry_count}) para que o job possa aplicar
     * uma política de backoff ou descartar o evento após o limite máximo.
     *
     * @param error descrição do erro ocorrido durante o processamento
     */
    public void markAsFailed(final String error) {
        this.errorMessage = error;
        this.retryCount++;
    }

    /**
     * Indica se o evento já foi processado com sucesso.
     *
     * @return {@code true} se {@code processed_at} estiver preenchido
     */
    public boolean isProcessed() {
        return processedAt != null;
    }

    // ── Composite PK ─────────────────────────────────────────

    /**
     * Classe de PK composta necessária para o mapeamento JPA da tabela particionada.
     *
     * <p>O PostgreSQL exige que a coluna de particionamento ({@code created_at}) faça parte
     * da chave primária em tabelas com particionamento por RANGE. Por isso, a PK desta entidade
     * é composta por {@code (id, created_at)}, e essa classe representa esse identificador
     * composto para o JPA (conforme especificado via {@link IdClass}).
     *
     * <p>Deve implementar {@link Serializable} e sobrescrever {@code equals} e {@code hashCode}
     * para que o cache de primeiro nível do Hibernate funcione corretamente.
     */
    public static class DomainEventEntityId implements Serializable {

        /** Parte 1 da PK: identificador único do evento. */
        private UUID id;

        /** Parte 2 da PK: instante de criação, exigido pelo particionamento por RANGE. */
        private Instant createdAt;

        /** Construtor padrão exigido pelo JPA (reflexão). */
        public DomainEventEntityId() {}

        public DomainEventEntityId(final UUID id, final Instant createdAt) {
            this.id = id;
            this.createdAt = createdAt;
        }

        /** Dois identificadores são iguais se e somente se ambos os campos coincidem. */
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
