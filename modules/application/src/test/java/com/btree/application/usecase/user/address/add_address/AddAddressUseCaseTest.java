package com.btree.application.usecase.user.address.add_address;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.error.AddressError;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Add address use case")
class AddAddressUseCaseTest {

    @Mock
    AddressGateway addressGateway;

    @Mock
    TransactionManager transactionManager;

    AddAddressUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddAddressUseCase(addressGateway, transactionManager);
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve cadastrar primeiro endereco marcando-o automaticamente como padrao")
    void givenFirstAddress_whenExecute_thenSetAsDefaultAutomatically() {
        final var userId = UserId.unique();

        when(addressGateway.countActiveByUserId(userId)).thenReturn(0L);
        when(addressGateway.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(validCommand(userId.getValue().toString()));

        assertTrue(result.isRight());
        final var output = result.get();
        assertTrue(output.isDefault());
        assertEquals(userId.getValue().toString(), output.userId());

        verify(addressGateway).countActiveByUserId(userId);
        verify(addressGateway).save(any(Address.class));
        verify(transactionManager).execute(any());
    }

    @Test
    @DisplayName("Deve cadastrar endereco adicional sem marca-lo como padrao")
    void givenAdditionalAddress_whenExecute_thenIsDefaultFalse() {
        final var userId = UserId.unique();

        when(addressGateway.countActiveByUserId(userId)).thenReturn(2L);
        when(addressGateway.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(validCommand(userId.getValue().toString()));

        assertTrue(result.isRight());
        assertFalse(result.get().isDefault());
        verify(addressGateway).countActiveByUserId(userId);
    }

    @Test
    @DisplayName("Deve mapear todos os campos do command para o output")
    void givenValidCommand_whenExecute_thenMapAllFieldsToOutput() {
        final var userId = UserId.unique();

        when(addressGateway.countActiveByUserId(userId)).thenReturn(0L);
        when(addressGateway.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new AddAddressCommand(
                userId.getValue().toString(),
                "Trabalho",
                "Carlos Lima",
                "Av. Paulista",
                "1000",
                "Conjunto 42",
                "Bela Vista",
                "São Paulo",
                "SP",
                "01310-100",
                "BR",
                true
        );

        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(userId.getValue().toString(), output.userId());
        assertEquals("Trabalho", output.label());
        assertEquals("Carlos Lima", output.recipientName());
        assertEquals("Av. Paulista", output.street());
        assertEquals("1000", output.number());
        assertEquals("Conjunto 42", output.complement());
        assertEquals("Bela Vista", output.neighborhood());
        assertEquals("São Paulo", output.city());
        assertEquals("SP", output.state());
        assertEquals("01310-100", output.postalCode());
        assertEquals("BR", output.country());
        assertTrue(output.isBillingAddress());
        assertNotNull(output.id());
        assertNotNull(output.createdAt());
    }

    @Test
    @DisplayName("Deve usar 'BR' como pais padrao quando country for nulo")
    void givenNullCountry_whenExecute_thenDefaultToBR() {
        final var userId = UserId.unique();

        when(addressGateway.countActiveByUserId(userId)).thenReturn(0L);
        when(addressGateway.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new AddAddressCommand(
                userId.getValue().toString(),
                "Casa", "Maria", "Rua A", "1", null, "Centro",
                "Curitiba", "PR", "80010-010", null, false
        );

        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertEquals("BR", result.get().country());
    }

    @Test
    @DisplayName("Deve retornar erro quando user id for nulo")
    void givenNullUserId_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(validCommand(null));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando user id estiver em branco")
    void givenBlankUserId_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(validCommand("   "));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando user id nao for UUID valido")
    void givenInvalidUUID_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(validCommand("not-a-valid-uuid"));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando street estiver vazio")
    void givenEmptyStreet_whenExecute_thenReturnValidationError() {
        final var userId = UserId.unique();
        when(addressGateway.countActiveByUserId(userId)).thenReturn(0L);

        final var command = new AddAddressCommand(
                userId.getValue().toString(),
                "Casa", "Maria", "", "1", null, "Centro",
                "São Paulo", "SP", "01310-100", "BR", false
        );

        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.STREET_EMPTY.message());
        verify(transactionManager, never()).execute(any());
        verify(addressGateway, never()).save(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando city estiver vazia")
    void givenEmptyCity_whenExecute_thenReturnValidationError() {
        final var userId = UserId.unique();
        when(addressGateway.countActiveByUserId(userId)).thenReturn(0L);

        final var command = new AddAddressCommand(
                userId.getValue().toString(),
                "Casa", "Maria", "Rua A", "1", null, "Centro",
                "", "SP", "01310-100", "BR", false
        );

        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.CITY_EMPTY.message());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando state nao tiver 2 letras maiusculas")
    void givenInvalidState_whenExecute_thenReturnValidationError() {
        final var userId = UserId.unique();
        when(addressGateway.countActiveByUserId(userId)).thenReturn(0L);

        final var command = new AddAddressCommand(
                userId.getValue().toString(),
                "Casa", "Maria", "Rua A", "1", null, "Centro",
                "São Paulo", "sao-paulo", "01310-100", "BR", false
        );

        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.STATE_INVALID.message());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando postalCode for invalido")
    void givenInvalidPostalCode_whenExecute_thenReturnValidationError() {
        final var userId = UserId.unique();
        when(addressGateway.countActiveByUserId(userId)).thenReturn(0L);

        final var command = new AddAddressCommand(
                userId.getValue().toString(),
                "Casa", "Maria", "Rua A", "1", null, "Centro",
                "São Paulo", "SP", "INVALID-CEP", "BR", false
        );

        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.POSTAL_CODE_INVALID.message());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve acumular multiplos erros de validacao no mesmo notification")
    void givenMultipleInvalidFields_whenExecute_thenAccumulateAllErrors() {
        final var userId = UserId.unique();
        when(addressGateway.countActiveByUserId(userId)).thenReturn(0L);

        final var command = new AddAddressCommand(
                userId.getValue().toString(),
                null, null, "", null, null, null,
                "", "invalido", "", "BR", false
        );

        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().size() > 1);
        assertError(result.getLeft(), AddressError.STREET_EMPTY.message());
        assertError(result.getLeft(), AddressError.CITY_EMPTY.message());
        assertError(result.getLeft(), AddressError.STATE_INVALID.message());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando a transacao falhar")
    void givenTransactionFailure_whenExecute_thenReturnNotification() {
        final var userId = UserId.unique();

        when(addressGateway.countActiveByUserId(userId)).thenReturn(0L);
        doThrow(new RuntimeException("db connection lost"))
                .when(transactionManager).execute(any());

        final var result = useCase.execute(validCommand(userId.getValue().toString()));

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(addressGateway, never()).save(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private AddAddressCommand validCommand(final String userId) {
        return new AddAddressCommand(
                userId,
                "Casa",
                "João Silva",
                "Rua das Flores",
                "123",
                "Apto 4",
                "Centro",
                "São Paulo",
                "SP",
                "01310-100",
                "BR",
                false
        );
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro com mensagem: " + message
        );
    }
}
