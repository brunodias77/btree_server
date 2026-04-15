package com.btree.application.usecase.job.clean_expired_tokens;

import com.btree.domain.user.gateway.UserTokenGateway;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.JobResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleanupExpiredTokensJobTest {

    @Mock UserTokenGateway userTokenGateway;
    @Mock TransactionManager transactionManager;

    CleanupExpiredTokensJob job;

    @BeforeEach
    void setUp() {
        job = new CleanupExpiredTokensJob(userTokenGateway, transactionManager);
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    void givenExpiredTokensExist_whenExecute_thenDeleteAndReturnCount() {
        when(userTokenGateway.deleteExpired(500)).thenReturn(42);

        final var result = job.execute(new CleanupExpiredTokens(500));

        assertTrue(result.isRight());
        final JobResult jobResult = result.get();
        assertEquals(42, jobResult.processed());
        assertEquals(0, jobResult.skipped());
        assertEquals(0, jobResult.failed());
    }

    @Test
    void givenNoExpiredTokens_whenExecute_thenReturnZeroCount() {
        when(userTokenGateway.deleteExpired(anyInt())).thenReturn(0);

        final var result = job.execute(CleanupExpiredTokens.withDefaultBatch());

        assertTrue(result.isRight());
        assertEquals(0, result.get().processed());
    }

    @Test
    void givenCustomBatchSize_whenExecute_thenPassBatchSizeToGateway() {
        when(userTokenGateway.deleteExpired(100)).thenReturn(10);

        job.execute(new CleanupExpiredTokens(100));

        verify(userTokenGateway).deleteExpired(100);
    }

    @Test
    void givenDatabaseException_whenExecute_thenReturnLeft() {
        doThrow(new RuntimeException("DB unavailable")).when(transactionManager).execute(any());

        final var result = job.execute(CleanupExpiredTokens.withDefaultBatch());

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().contains("DB unavailable")));
    }

    @Test
    void givenDefaultBatch_whenExecute_thenUseBatchSize500() {
        when(userTokenGateway.deleteExpired(500)).thenReturn(0);

        job.execute(CleanupExpiredTokens.withDefaultBatch());

        verify(userTokenGateway).deleteExpired(500);
    }

    @Test
    void givenInvalidBatchSize_whenCreateCommand_thenThrowIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new CleanupExpiredTokens(0));
        assertThrows(IllegalArgumentException.class, () -> new CleanupExpiredTokens(-1));
    }
}
