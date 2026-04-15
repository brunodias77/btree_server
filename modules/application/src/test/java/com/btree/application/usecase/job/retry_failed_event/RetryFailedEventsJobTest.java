package com.btree.application.usecase.job.retry_failed_event;

import com.btree.shared.contract.TransactionManager;
import com.btree.shared.gateway.OutboxEventGateway;
import com.btree.shared.gateway.OutboxEventGateway.PendingEvent;
import com.btree.shared.gateway.ProcessedEventGateway;
import com.btree.shared.usecase.JobResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Retry failed events job")
class RetryFailedEventsJobTest {

    @Mock OutboxEventGateway outboxEventGateway;
    @Mock ProcessedEventGateway processedEventGateway;
    @Mock TransactionManager transactionManager;

    RetryFailedEventsJob job;

    @BeforeEach
    void setUp() {
        job = new RetryFailedEventsJob(outboxEventGateway, processedEventGateway, transactionManager);
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PendingEvent buildEvent() {
        return new PendingEvent(UUID.randomUUID(), Instant.now(), "SomeEvent", "catalog");
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar resultado vazio quando nao houver eventos retentaveis")
    void givenNoRetryableEvents_whenExecute_thenReturnEmpty() {
        when(outboxEventGateway.findRetryable(anyInt(), anyInt())).thenReturn(List.of());

        final var result = job.execute(new RetryFailedEvents(50, 5));

        assertTrue(result.isRight());
        final JobResult jr = result.get();
        assertEquals(0, jr.processed());
        assertEquals(0, jr.skipped());
        assertEquals(0, jr.failed());
        assertEquals(0, jr.total());
    }

    @Test
    @DisplayName("Deve retentar todos os eventos retentaveis")
    void givenRetryableEvents_whenExecute_thenRetryAll() {
        final var event1 = buildEvent();
        final var event2 = buildEvent();
        when(outboxEventGateway.findRetryable(5, 50)).thenReturn(List.of(event1, event2));
        when(processedEventGateway.alreadyProcessed(any())).thenReturn(false);
        doNothing().when(processedEventGateway).recordProcessed(any(), any(), any());
        doNothing().when(outboxEventGateway).markAsProcessed(any(), any());

        final var result = job.execute(new RetryFailedEvents(50, 5));

        assertTrue(result.isRight());
        final JobResult jr = result.get();
        assertEquals(2, jr.processed());
        assertEquals(0, jr.skipped());
        assertEquals(0, jr.failed());
    }

    @Test
    @DisplayName("Deve registrar evento processado e marcar outbox como processado")
    void givenRetryableEvent_whenExecute_thenRecordProcessedAndMarkOutbox() {
        final var event = buildEvent();
        when(outboxEventGateway.findRetryable(anyInt(), anyInt())).thenReturn(List.of(event));
        when(processedEventGateway.alreadyProcessed(event.id())).thenReturn(false);
        doNothing().when(processedEventGateway).recordProcessed(any(), any(), any());
        doNothing().when(outboxEventGateway).markAsProcessed(any(), any());

        job.execute(RetryFailedEvents.withDefaults());

        verify(processedEventGateway).recordProcessed(event.id(), event.eventType(), event.module());
        verify(outboxEventGateway).markAsProcessed(event.id(), event.createdAt());
    }

    @Test
    @DisplayName("Deve pular evento ja processado mantendo idempotencia")
    void givenAlreadyProcessedEvent_whenRetrying_thenSkipWithIdempotency() {
        final var event = buildEvent();
        when(outboxEventGateway.findRetryable(anyInt(), anyInt())).thenReturn(List.of(event));
        when(processedEventGateway.alreadyProcessed(event.id())).thenReturn(true);
        doNothing().when(outboxEventGateway).markAsProcessed(any(), any());

        final var result = job.execute(RetryFailedEvents.withDefaults());

        assertTrue(result.isRight());
        final JobResult jr = result.get();
        assertEquals(0, jr.processed());
        assertEquals(1, jr.skipped());
        assertEquals(0, jr.failed());
        verify(processedEventGateway, never()).recordProcessed(any(), any(), any());
        verify(outboxEventGateway).markAsProcessed(event.id(), event.createdAt());
    }

    @Test
    @DisplayName("Deve marcar evento como falho e continuar quando a retentativa falhar")
    void givenEventThatFailsDuringRetry_whenExecute_thenMarkAsFailedAndContinue() {
        final var failingEvent = buildEvent();
        final var goodEvent    = buildEvent();

        when(outboxEventGateway.findRetryable(anyInt(), anyInt()))
                .thenReturn(List.of(failingEvent, goodEvent));
        when(processedEventGateway.alreadyProcessed(failingEvent.id())).thenReturn(false);
        when(processedEventGateway.alreadyProcessed(goodEvent.id())).thenReturn(false);

        doThrow(new RuntimeException("Retry failed"))
                .doAnswer(inv -> {
                    java.util.function.Supplier<?> s = inv.getArgument(0);
                    return s.get();
                })
                .when(transactionManager).execute(any());

        doNothing().when(outboxEventGateway).markAsFailed(any(), any(), any());
        doNothing().when(processedEventGateway).recordProcessed(any(), any(), any());
        doNothing().when(outboxEventGateway).markAsProcessed(any(), any());

        final var result = job.execute(RetryFailedEvents.withDefaults());

        assertTrue(result.isRight());
        final JobResult jr = result.get();
        assertEquals(1, jr.processed());
        assertEquals(0, jr.skipped());
        assertEquals(1, jr.failed());
        verify(outboxEventGateway).markAsFailed(
                eq(failingEvent.id()), eq(failingEvent.createdAt()), anyString());
    }

    @Test
    @DisplayName("Deve retornar contadores corretos para eventos retentaveis mistos")
    void givenMixedRetryableEvents_whenExecute_thenReturnCorrectCounters() {
        final var goodEvent    = buildEvent();
        final var doneEvent    = buildEvent();
        final var failingEvent = buildEvent();

        when(outboxEventGateway.findRetryable(anyInt(), anyInt()))
                .thenReturn(List.of(goodEvent, doneEvent, failingEvent));

        when(processedEventGateway.alreadyProcessed(goodEvent.id())).thenReturn(false);
        when(processedEventGateway.alreadyProcessed(doneEvent.id())).thenReturn(true);
        when(processedEventGateway.alreadyProcessed(failingEvent.id())).thenReturn(false);

        doAnswer(inv -> { // goodEvent OK
                    java.util.function.Supplier<?> s = inv.getArgument(0);
                    return s.get();
                })
                .doThrow(new RuntimeException("fail")) // failingEvent KO
                .when(transactionManager).execute(any());

        doNothing().when(outboxEventGateway).markAsProcessed(any(), any());
        doNothing().when(processedEventGateway).recordProcessed(any(), any(), any());
        doNothing().when(outboxEventGateway).markAsFailed(any(), any(), any());

        final var result = job.execute(RetryFailedEvents.withDefaults());

        assertTrue(result.isRight());
        final JobResult jr = result.get();
        assertEquals(1, jr.processed()); // retried
        assertEquals(1, jr.skipped());
        assertEquals(1, jr.failed());
    }

    @Test
    @DisplayName("Deve retornar erro quando a busca por eventos retentaveis falhar")
    void givenFindRetryableThrows_whenExecute_thenReturnLeft() {
        when(outboxEventGateway.findRetryable(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("DB error"));

        final var result = job.execute(RetryFailedEvents.withDefaults());

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
    }

    @Test
    @DisplayName("Deve usar max retries informado no comando ao buscar eventos")
    void givenMaxRetriesPassedToGateway_whenExecute_thenUseCommandMaxRetries() {
        when(outboxEventGateway.findRetryable(3, 20)).thenReturn(List.of());

        job.execute(new RetryFailedEvents(20, 3));

        verify(outboxEventGateway).findRetryable(3, 20);
    }

    @Test
    @DisplayName("Deve rejeitar argumentos invalidos ao criar o comando")
    void givenInvalidCommandArgs_whenCreate_thenThrowIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RetryFailedEvents(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new RetryFailedEvents(50, 0));
        assertThrows(IllegalArgumentException.class, () -> new RetryFailedEvents(-1, -1));
    }
}
