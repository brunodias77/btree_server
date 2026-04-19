package com.btree.application.usecase.user.address.set_default_address;

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
@DisplayName("Set default address use case")
class SetDefaultAddressUseCaseTest {

    @Mock
    AddressGateway addressGateway;

    @Mock
    TransactionManager transactionManager;

    SetDefaultAddressUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SetDefaultAddressUseCase(addressGateway, transactionManager);
        lenient().doAnswer(inv -> {
            java.util.function.Supplier<?> s = inv.getArgument(0);
            return s.get();
        }).when(transactionManager).execute(any());
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve marcar endereco nao-padrao como padrao com sucesso")
    void givenNonDefaultAddress_whenExecute_thenClearAndSetDefault() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildAddress(addressId, userId, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));
        when(addressGateway.update(address)).thenReturn(address);

        final var result = useCase.execute(new SetDefaultAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString()
        ));

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(addressId.getValue().toString(), output.id());
        assertTrue(output.isDefault());

        verify(addressGateway).findById(addressId);
        verify(addressGateway).clearDefaultByUserId(userId);
        verify(addressGateway).update(address);
        verify(transactionManager).execute(any());
    }

    @Test
    @DisplayName("Deve ser idempotente quando endereco ja e padrao")
    void givenAlreadyDefaultAddress_whenExecute_thenReturnSuccessWithoutModifications() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildAddress(addressId, userId, true);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));

        final var result = useCase.execute(new SetDefaultAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString()
        ));

        assertTrue(result.isRight());
        assertTrue(result.get().isDefault());

        verify(addressGateway).findById(addressId);
        verify(addressGateway, never()).clearDefaultByUserId(any());
        verify(addressGateway, never()).update(any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve mapear todos os campos do output corretamente")
    void givenNonDefaultAddress_whenExecute_thenOutputFieldsAreMapped() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildAddress(addressId, userId, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));
        when(addressGateway.update(address)).thenReturn(address);

        final var output = useCase.execute(new SetDefaultAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString()
        )).get();

        assertEquals(addressId.getValue().toString(), output.id());
        assertEquals(userId.getValue().toString(), output.userId());
        assertEquals("Casa", output.label());
        assertEquals("João Silva", output.recipientName());
        assertEquals("Rua das Flores", output.street());
        assertEquals("123", output.number());
        assertEquals("Centro", output.neighborhood());
        assertEquals("São Paulo", output.city());
        assertEquals("SP", output.state());
        assertEquals("01310-100", output.postalCode());
        assertEquals("BR", output.country());
        assertNotNull(output.createdAt());
    }

    @Test
    @DisplayName("Deve retornar erro quando userId for nulo")
    void givenNullUserId_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(new SetDefaultAddressCommand(
                null, AddressId.unique().getValue().toString()
        ));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando userId estiver em branco")
    void givenBlankUserId_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(new SetDefaultAddressCommand(
                "   ", AddressId.unique().getValue().toString()
        ));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando userId nao for UUID valido")
    void givenInvalidUserId_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(new SetDefaultAddressCommand(
                "not-a-uuid", AddressId.unique().getValue().toString()
        ));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando addressId for nulo")
    void givenNullAddressId_whenExecute_thenReturnAddressNotFound() {
        final var result = useCase.execute(new SetDefaultAddressCommand(
                UserId.unique().getValue().toString(), null
        ));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.ADDRESS_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando addressId estiver em branco")
    void givenBlankAddressId_whenExecute_thenReturnAddressNotFound() {
        final var result = useCase.execute(new SetDefaultAddressCommand(
                UserId.unique().getValue().toString(), "   "
        ));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.ADDRESS_NOT_FOUND.message());
        verifyNoInteractions(addressGateway, transactionManager);
    }

    @Test
    @DisplayName("Deve retornar erro quando addressId nao for UUID valido")
    void givenInvalidAddressId_whenExecute_thenReturnAddressNotFound() {
        final var result = useCase.execute(new SetDefaultAddressCommand(
                UserId.unique().getValue().toString(), "not-a-uuid"
        ));

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

        final var result = useCase.execute(new SetDefaultAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString()
        ));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.ADDRESS_NOT_FOUND.message());
        verify(addressGateway).findById(addressId);
        verify(addressGateway, never()).update(any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando endereco ja estiver soft-deletado")
    void givenAlreadyDeletedAddress_whenExecute_thenReturnAddressAlreadyDeleted() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildDeletedAddress(addressId, userId);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));

        final var result = useCase.execute(new SetDefaultAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString()
        ));

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
        final var address        = buildAddress(addressId, actualOwner, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));

        final var result = useCase.execute(new SetDefaultAddressCommand(
                requestingUser.getValue().toString(),
                addressId.getValue().toString()
        ));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), AddressError.ADDRESS_BELONGS_TO_ANOTHER_USER.message());
        verify(addressGateway, never()).update(any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando a transacao falhar")
    void givenTransactionFailure_whenExecute_thenReturnNotification() {
        final var userId    = UserId.unique();
        final var addressId = AddressId.unique();
        final var address   = buildAddress(addressId, userId, false);

        when(addressGateway.findById(addressId)).thenReturn(Optional.of(address));
        doThrow(new RuntimeException("db down")).when(transactionManager).execute(any());

        final var result = useCase.execute(new SetDefaultAddressCommand(
                userId.getValue().toString(),
                addressId.getValue().toString()
        ));

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(addressGateway, never()).update(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Address buildAddress(final AddressId addressId, final UserId userId, final boolean isDefault) {
        final var now = Instant.now();
        return Address.with(
                addressId, userId,
                "Casa", "João Silva",
                "Rua das Flores", "123", null, "Centro",
                "São Paulo", "SP", "01310-100", "BR",
                null, null, null,
                isDefault, false,
                now, now, null
        );
    }

    private Address buildDeletedAddress(final AddressId addressId, final UserId userId) {
        final var now = Instant.now();
        return Address.with(
                addressId, userId,
                "Casa", "João Silva",
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
