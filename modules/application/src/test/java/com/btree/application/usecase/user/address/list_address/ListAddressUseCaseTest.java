package com.btree.application.usecase.user.address.list_address;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("List address use case")
class ListAddressUseCaseTest {

    @Mock
    AddressGateway addressGateway;

    ListAddressUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListAddressUseCase(addressGateway);
    }

    // ── testes ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve retornar lista de enderecos quando usuario possuir enderecos cadastrados")
    void givenValidUserId_whenExecute_thenReturnAddressList() {
        final var userId = UserId.unique();
        final var address1 = buildAddress(userId, true, false);
        final var address2 = buildAddress(userId, false, true);

        when(addressGateway.findByUserId(userId)).thenReturn(List.of(address1, address2));

        final var result = useCase.execute(new ListAddressCommand(userId.getValue().toString()));

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(2, output.items().size());

        final var item1 = output.items().get(0);
        assertEquals(address1.getId().getValue().toString(), item1.id());
        assertEquals(userId.getValue().toString(), item1.userId());
        assertEquals("Casa", item1.label());
        assertEquals("Rua das Flores", item1.street());
        assertEquals("SP", item1.state());
        assertEquals("01310-100", item1.postalCode());
        assertEquals("BR", item1.country());
        assertTrue(item1.isDefault());
        assertFalse(item1.isBillingAddress());

        final var item2 = output.items().get(1);
        assertEquals(address2.getId().getValue().toString(), item2.id());
        assertFalse(item2.isDefault());
        assertTrue(item2.isBillingAddress());

        verify(addressGateway).findByUserId(userId);
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando usuario nao possuir enderecos")
    void givenValidUserId_whenUserHasNoAddresses_thenReturnEmptyList() {
        final var userId = UserId.unique();

        when(addressGateway.findByUserId(userId)).thenReturn(List.of());

        final var result = useCase.execute(new ListAddressCommand(userId.getValue().toString()));

        assertTrue(result.isRight());
        assertTrue(result.get().items().isEmpty());
        verify(addressGateway).findByUserId(userId);
    }

    @Test
    @DisplayName("Deve retornar erro quando user id for nulo")
    void givenNullUserId_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(new ListAddressCommand(null));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway);
    }

    @Test
    @DisplayName("Deve retornar erro quando user id estiver em branco")
    void givenBlankUserId_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(new ListAddressCommand("   "));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway);
    }

    @Test
    @DisplayName("Deve retornar erro quando user id nao for um UUID valido")
    void givenInvalidUUID_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(new ListAddressCommand("not-a-valid-uuid"));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(addressGateway);
    }

    @Test
    @DisplayName("Deve mapear todos os campos do endereco no output")
    void givenValidUserId_whenExecute_thenMapAllAddressFields() {
        final var userId = UserId.unique();
        final var now = Instant.parse("2026-04-19T10:00:00Z");
        final var address = Address.with(
                AddressId.unique(),
                userId,
                "Trabalho",
                "João Silva",
                "Av. Paulista",
                "1000",
                "Conjunto 42",
                "Bela Vista",
                "São Paulo",
                "SP",
                "01310-100",
                "BR",
                null, null, null,
                false, true,
                now, now, null
        );

        when(addressGateway.findByUserId(userId)).thenReturn(List.of(address));

        final var result = useCase.execute(new ListAddressCommand(userId.getValue().toString()));

        assertTrue(result.isRight());
        final var item = result.get().items().get(0);
        assertEquals(address.getId().getValue().toString(), item.id());
        assertEquals(userId.getValue().toString(), item.userId());
        assertEquals("Trabalho", item.label());
        assertEquals("João Silva", item.recipientName());
        assertEquals("Av. Paulista", item.street());
        assertEquals("1000", item.number());
        assertEquals("Conjunto 42", item.complement());
        assertEquals("Bela Vista", item.neighborhood());
        assertEquals("São Paulo", item.city());
        assertEquals("SP", item.state());
        assertEquals("01310-100", item.postalCode());
        assertEquals("BR", item.country());
        assertFalse(item.isDefault());
        assertTrue(item.isBillingAddress());
        assertEquals(now, item.createdAt());
        assertEquals(now, item.updatedAt());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Address buildAddress(final UserId userId, final boolean isDefault, final boolean isBillingAddress) {
        final var now = Instant.now();
        return Address.with(
                AddressId.unique(),
                userId,
                "Casa",
                "Maria Souza",
                "Rua das Flores",
                "123",
                "Apto 4",
                "Centro",
                "São Paulo",
                "SP",
                "01310-100",
                "BR",
                null, null, null,
                isDefault, isBillingAddress,
                now, now, null
        );
    }

    private void assertError(final Notification notification, final String message) {
        assertTrue(notification.getErrors().stream().anyMatch(e -> e.message().equals(message)));
    }
}
