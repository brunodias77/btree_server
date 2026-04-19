//package com.btree.infrastructure.user.persistence;
//
//import com.btree.infrastructure.IntegrationTest;
//import com.btree.infrastructure.event.DomainEventEntity;
//import com.btree.infrastructure.event.DomainEventJpaRepository;
//import com.btree.infrastructure.event.OutboxEventPostgresGateway;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//
//import java.time.Instant;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@DisplayName("Integration Test: OutboxEventPostgresGateway")
//class OutboxEventPostgresGatewayTest extends IntegrationTest {
//
//    @Autowired
//    private OutboxEventPostgresGateway gateway;
//
//    @Autowired
//    private DomainEventJpaRepository repository;
//
//    @BeforeEach
//    void cleanUp() {
//        repository.deleteAll();
//    }
//
//    @Test
//    @DisplayName("Deve marcar evento na tabela de outbound como processado com sucesso")
//    void shouldMarkAsProcessed() {
//        final var id = UUID.randomUUID();
//        final var now = Instant.now();
//        var entity = DomainEventEntity.builder()
//                .id(id)
//                .createdAt(now)
//                .module("users")
//                .aggregateType("User")
//                .aggregateId(UUID.randomUUID())
//                .eventType("UserCreatedEvent")
//                .payload("{}")
//                .build();
//        repository.save(entity);
//
//        gateway.markAsProcessed(id, now);
//
//        var dbEntity = repository.findById(new DomainEventEntity.DomainEventEntityId(id, now));
//        assertThat(dbEntity).isPresent();
//        assertThat(dbEntity.get().isProcessed()).isTrue();
//        assertThat(dbEntity.get().getErrorMessage()).isNull();
//    }
//
//    @Test
//    @DisplayName("Deve marcar evento como falho, registrando a stacktrace e incrementando retry_count")
//    void shouldMarkAsFailed() {
//        final var id = UUID.randomUUID();
//        final var now = Instant.now();
//        var entity = DomainEventEntity.builder()
//                .id(id)
//                .createdAt(now)
//                .module("users")
//                .aggregateType("User")
//                .aggregateId(UUID.randomUUID())
//                .eventType("UserCreatedEvent")
//                .payload("{}")
//                .build();
//        repository.save(entity);
//
//        gateway.markAsFailed(id, now, "Timeout aguardando Broker de Mensagens");
//
//        var dbEntity = repository.findById(new DomainEventEntity.DomainEventEntityId(id, now));
//        assertThat(dbEntity).isPresent();
//        assertThat(dbEntity.get().isProcessed()).isFalse();
//        assertThat(dbEntity.get().getErrorMessage()).isEqualTo("Timeout aguardando Broker de Mensagens");
//        assertThat(dbEntity.get().getRetryCount()).isEqualTo(1);
//    }
//}
