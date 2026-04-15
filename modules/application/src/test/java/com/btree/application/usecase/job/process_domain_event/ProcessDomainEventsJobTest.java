package com.btree.application.usecase.job.process_domain_event;

import com.btree.shared.contract.TransactionManager;
import com.btree.shared.gateway.OutboxEventGateway;
import com.btree.shared.gateway.OutboxEventGateway.PendingEvent;
import com.btree.shared.gateway.ProcessedEventGateway;
import com.btree.shared.usecase.JobResult;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessDomainEventsJobTest {

    @Mock OutboxEventGateway outboxEventGateway;
    @Mock ProcessedEventGateway processedEventGateway;
    @Mock TransactionManager transactionManager;

    ProcessDomainEventsJob job;

    @BeforeEach
    void setUp() {
        job = new ProcessDomainEventsJob(outboxEventGateway, processedEventGateway, transactionManager);
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PendingEvent buildEvent() {
        return new PendingEvent(UUID.randomUUID(), Instant.now(), "UserCreatedEvent", "users");
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    void givenNoPendingEvents_whenExecute_thenReturnEmpty() {
        when(outboxEventGateway.findPending(anyInt())).thenReturn(List.of());

        final var result = job.execute(new ProcessDomainEvents(100));

        assertTrue(result.isRight());
        final JobResult jr = result.get();
        assertEquals(0, jr.processed());
        assertEquals(0, jr.skipped());
        assertEquals(0, jr.failed());
        assertEquals(0, jr.total());
    }

    @Test
    void givenPendingNewEvents_whenExecute_thenProcessAll() {
        final var event1 = buildEvent();
        final var event2 = buildEvent();
        when(outboxEventGateway.findPending(100)).thenReturn(List.of(event1, event2));
        when(processedEventGateway.alreadyProcessed(any())).thenReturn(false);
        doNothing().when(processedEventGateway).recordProcessed(any(), any(), any());
        doNothing().when(outboxEventGateway).markAsProcessed(any(), any());

        final var result = job.execute(new ProcessDomainEvents(100));

        assertTrue(result.isRight());
        final JobResult jr = result.get();
        assertEquals(2, jr.processed());
        assertEquals(0, jr.skipped());
        assertEquals(0, jr.failed());
    }

    @Test
    void givenNewEvent_whenExecute_thenRecordProcessedAndMarkOutbox() {
        final var event = buildEvent();
        when(outboxEventGateway.findPending(anyInt())).thenReturn(List.of(event));
        when(processedEventGateway.alreadyProcessed(event.id())).thenReturn(false);
        doNothing().when(processedEventGateway).recordProcessed(any(), any(), any());
        doNothing().when(outboxEventGateway).markAsProcessed(any(), any());

        job.execute(ProcessDomainEvents.withDefaultBatch());

        verify(processedEventGateway).recordProcessed(event.id(), event.eventType(), event.module());
        verify(outboxEventGateway).markAsProcessed(event.id(), event.createdAt());
    }

    @Test
    void givenAlreadyProcessedEvent_whenExecute_thenSkipWithIdempotency() {
        final var event = buildEvent();
        when(outboxEventGateway.findPending(anyInt())).thenReturn(List.of(event));
        when(processedEventGateway.alreadyProcessed(event.id())).thenReturn(true);
        doNothing().when(outboxEventGateway).markAsProcessed(any(), any());

        final var result = job.execute(ProcessDomainEvents.withDefaultBatch());

        assertTrue(result.isRight());
        final JobResult jr = result.get();
        assertEquals(0, jr.processed());
        assertEquals(1, jr.skipped());
        assertEquals(0, jr.failed());
        verify(processedEventGateway, never()).recordProcessed(any(), any(), any());
    }

    @Test
    void givenEventThatFailsDuringTransaction_whenExecute_thenMarkAsFailedAndContinue() {
        final var failingEvent = buildEvent();
        final var goodEvent = buildEvent();

        when(outboxEventGateway.findPending(anyInt())).thenReturn(List.of(failingEvent, goodEvent));
        when(processedEventGateway.alreadyProcessed(failingEvent.id())).thenReturn(false);
        when(processedEventGateway.alreadyProcessed(goodEvent.id())).thenReturn(false);

        // First event fails in transaction, second succeeds
        doThrow(new RuntimeException("Transient error"))
                .doAnswer(inv -> {
                    java.util.function.Supplier<?> s = inv.getArgument(0);
                    return s.get();
                })
                .when(transactionManager).execute(any());

        doNothing().when(outboxEventGateway).markAsFailed(any(), any(), any());
        doNothing().when(processedEventGateway).recordProcessed(any(), any(), any());
        doNothing().when(outboxEventGateway).markAsProcessed(any(), any());

        final var result = job.execute(ProcessDomainEvents.withDefaultBatch());

        assertTrue(result.isRight());
        final JobResult jr = result.get();
        assertEquals(1, jr.processed());
        assertEquals(0, jr.skipped());
        assertEquals(1, jr.failed());
        verify(outboxEventGateway).markAsFailed(eq(failingEvent.id()), eq(failingEvent.createdAt()), anyString());
    }

    @Test
    void givenMixedEvents_whenExecute_thenReturnCorrectCounters() {
        final var newEvent    = buildEvent();
        final var doneEvent   = buildEvent();
        final var failingEvent = buildEvent();

        when(outboxEventGateway.findPending(anyInt()))
                .thenReturn(List.of(newEvent, doneEvent, failingEvent));

        when(processedEventGateway.alreadyProcessed(newEvent.id())).thenReturn(false);
        when(processedEventGateway.alreadyProcessed(doneEvent.id())).thenReturn(true);
        when(processedEventGateway.alreadyProcessed(failingEvent.id())).thenReturn(false);

        doAnswer(inv -> { // newEvent OK
                    java.util.function.Supplier<?> s = inv.getArgument(0);
                    return s.get();
                })
                .doThrow(new RuntimeException("fail")) // failingEvent KO
                .when(transactionManager).execute(any());

        doNothing().when(outboxEventGateway).markAsProcessed(any(), any());
        doNothing().when(processedEventGateway).recordProcessed(any(), any(), any());
        doNothing().when(outboxEventGateway).markAsFailed(any(), any(), any());

        final var result = job.execute(ProcessDomainEvents.withDefaultBatch());

        assertTrue(result.isRight());
        final JobResult jr = result.get();
        assertEquals(1, jr.processed());
        assertEquals(1, jr.skipped());
        assertEquals(1, jr.failed());
    }

    @Test
    void givenGatewayException_whenFindingPendingEvents_thenReturnLeft() {
        when(outboxEventGateway.findPending(anyInt())).thenThrow(new RuntimeException("Connection lost"));

        final var result = job.execute(ProcessDomainEvents.withDefaultBatch());

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
    }

    @Test
    void givenInvalidBatchSize_whenCreateCommand_thenThrowIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessDomainEvents(0));
        assertThrows(IllegalArgumentException.class, () -> new ProcessDomainEvents(-5));
    }
}
