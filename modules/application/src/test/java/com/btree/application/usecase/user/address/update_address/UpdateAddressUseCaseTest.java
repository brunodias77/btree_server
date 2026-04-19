package com.btree.application.usecase.user.address.update_address;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Update address use case")
class UpdateAddressUseCaseTest {

    @Mock
    AddressGateway addressGateway;

    @Mock
    TransactionManager transactionManager;

    UpdateAddressUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateAddressUseCase(addressGateway, transactionManager);
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve atualizar todos os campos do endereco com sucesso")
    void givenValidCommand_whenExecute_thenUpdateAndReturnOutput() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildAddress(addressId, userId, false, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));
        when(addressGateway.update(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new UpdateAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString(),
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
        assertEquals(addressId.getValue().toString(), output.id());
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

        verify(addressGateway).findById(addressId);
        verify(addressGateway).update(address);
        verify(transactionManager).execute(any());
    }

    @Test
    @DisplayName("Deve usar 'BR' como pais padrao quando country for nulo no comando")
    void givenNullCountry_whenExecute_thenDefaultToBR() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildAddress(addressId, userId, false, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));
        when(addressGateway.update(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        final var command = new UpdateAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString(),
                null, null, "Rua A", "1", null, "Centro",
                "Curitiba", "PR", "80010-010", null, false
        );

        final var result = useCase.execute(command);

        assertTrue(result.isRight());
        assertEquals("BR", result.get().country());
    }

    @Test
    @DisplayName("Deve preservar isDefault do endereco original apos a atualizacao")
    void givenDefaultAddress_whenExecute_thenKeepIsDefaultTrue() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildAddress(addressId, userId, true, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));
        when(addressGateway.update(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        final var result = useCase.execute(validCommand(userId, addressId));

        assertTrue(result.isRight());
        assertTrue(result.get().isDefault());
    }

    @Test
    @DisplayName("Deve retornar erro quando userId for nulo")
    void givenNullUserId_whenExecute_thenReturnUserNotFound() {
        final var addressId = AddressId.unique();

        final var result = useCase.execute(commandWithUserId(null, addressId.getValue().toString()));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando userId estiver em branco")
    void givenBlankUserId_whenExecute_thenReturnUserNotFound() {
        final var addressId = AddressId.unique();

        final var result = useCase.execute(commandWithUserId("   ", addressId.getValue().toString()));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando userId nao for UUID valido")
    void givenInvalidUserId_whenExecute_thenReturnUserNotFound() {
        final var addressId = AddressId.unique();

        final var result = useCase.execute(commandWithUserId("not-a-uuid", addressId.getValue().toString()));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando addressId for nulo")
    void givenNullAddressId_whenExecute_thenReturnAddressNotFound() {
        final var userId = UserId.unique();

        final var result = useCase.execute(commandWithUserId(userId.getValue().toString(), null));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.ADDRESS_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando addressId estiver em branco")
    void givenBlankAddressId_whenExecute_thenReturnAddressNotFound() {
        final var userId = UserId.unique();

        final var result = useCase.execute(commandWithUserId(userId.getValue().toString(), "   "));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.ADDRESS_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando addressId nao for UUID valido")
    void givenInvalidAddressId_whenExecute_thenReturnAddressNotFound() {
        final var userId = UserId.unique();

        final var result = useCase.execute(commandWithUserId(userId.getValue().toString(), "not-a-uuid"));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.ADDRESS_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando endereco nao for encontrado no gateway")
    void givenAddressNotFound_whenExecute_thenReturnAddressNotFound() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();

        when(addressGateway.findById(addressId)).thenReturn(Optional.empty());

        final var result = useCase.execute(validCommand(userId, addressId));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.ADDRESS_NOT_FOUND.message());
        verify(addressGateway).findById(addressId);
        verify(addressGateway, never()).update(any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando endereco ja estiver soft-deletado")
    void givenDeletedAddress_whenExecute_thenReturnAddressAlreadyDeleted() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildDeletedAddress(addressId, userId);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));

        final var result = useCase.execute(validCommand(userId, addressId));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.ADDRESS_ALREADY_DELETED.message());
        verify(addressGateway, never()).update(any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando endereco pertencer a outro usuario")
    void givenAddressBelongingToAnotherUser_whenExecute_thenReturnOwnershipError() {
        final var requestingUser = UserId.unique();
        final var actualOwner    = UserId.unique();
        final var addressId      = AddressId.unique();
        final var address        = buildAddress(addressId, actualOwner, false, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));

        final var result = useCase.execute(validCommand(requestingUser, addressId));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER.message());
        verify(addressGateway, never()).update(any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro de validacao quando street estiver vazio apos updateData")
    void givenEmptyStreet_whenExecute_thenReturnDomainValidationError() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildAddress(addressId, userId, false, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));

        final var command = new UpdateAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString(),
                null, null, "", null, null, null,
                "São Paulo", "SP", "01310-100", "BR", false
        );

        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.STREET_EMPTY.message());
        verify(addressGateway, never()).update(any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro de validacao quando state for invalido apos updateData")
    void givenInvalidState_whenExecute_thenReturnDomainValidationError() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildAddress(addressId, userId, false, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));

        final var command = new UpdateAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString(),
                null, null, "Rua A", null, null, null,
                "São Paulo", "invalido", "01310-100", "BR", false
        );

        final var result = useCase.execute(command);

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.STATE_INVALID.message());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando a transacao falhar")
    void givenTransactionFailure_whenExecute_thenReturnNotification() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildAddress(addressId, userId, false, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));
        doThrow(new RuntimeException("db down")).when(transactionManager).execute(any());

        final var result = useCase.execute(validCommand(userId, addressId));

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(addressGateway, never()).update(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UpdateAddressCommand validCommand(final UserId userId, final AddressId addressId) {
        return new UpdateAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString(),
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

    private UpdateAddressCommand commandWithUserId(final String userId, final String addressId) {
        return new UpdateAddressCommand(
                userId,
                addressId,
                null, null,
                "Rua das Flores", "123", null, "Centro",
                "São Paulo", "SP", "01310-100", "BR", false
        );
    }

    private Address buildAddress(
            final AddressId addressId,
            final UserId userId,
            final boolean isDefault,
            final boolean isBillingAddress
    ) {
        final var now = Instant.now();
        return Address.with(
                addressId, userId,
                "Casa", "Maria Souza",
                "Rua das Flores", "123", "Apto 4", "Centro",
                "São Paulo", "SP", "01310-100", "BR",
                null, null, null,
                isDefault, isBillingAddress,
                now, now, null
        );
    }

    private Address buildDeletedAddress(final AddressId addressId, final UserId userId) {
        final var now = Instant.now();
        return Address.with(
                addressId, userId,
                "Casa", "Maria Souza",
                "Rua das Flores", "123", null, "Centro",
                "São Paulo", "SP", "01310-100", "BR",
                null, null, null,
                false, false,
                now, now, now  // deletedAt = now → isDeleted() == true
        );
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(
                notification.getErrors().stream().anyMatch(e -> e.message().equals(message)),
                "Esperado erro com mensagem: " + message
        );
    }
}
