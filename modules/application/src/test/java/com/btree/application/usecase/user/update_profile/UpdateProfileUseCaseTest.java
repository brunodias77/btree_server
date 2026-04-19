package com.btree.application.usecase.user.update_profile;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.error.ProfileError;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.ProfileGateway;
import com.btree.domain.user.identifier.ProfileId;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Update profile use case")
class UpdateProfileUseCaseTest {

    @Mock ProfileGateway profileGateway;
    @Mock TransactionManager transactionManager;

    UpdateProfileUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateProfileUseCase(profileGateway, transactionManager);
        lenient().doAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        }).when(transactionManager).execute(any());
    }

    @Test
    @DisplayName("Deve atualizar perfil quando dados forem validos")
    void givenValidCommand_whenExecute_thenUpdateProfile() {
        final var userId = UserId.unique();
        final var profile = buildProfile(userId);
        final var birthDate = LocalDate.parse("1992-04-18");

        when(profileGateway.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileGateway.existsByCpfAndNotUserId("529.982.247-25", userId)).thenReturn(false);
        when(profileGateway.update(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final var result = useCase.execute(new UpdateProfileCommand(
                userId.getValue().toString(),
                "Bruno",
                "Dias",
                "529.982.247-25",
                birthDate,
                "M",
                "en-US",
                "USD",
                true
        ));

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(profile.getId().getValue().toString(), output.id());
        assertEquals(userId.getValue().toString(), output.userId());
        assertEquals("Bruno", output.firstName());
        assertEquals("Dias", output.lastName());
        assertEquals("Bruno Dias", output.displayName());
        assertEquals("529.982.247-25", output.cpf());
        assertEquals(birthDate, output.birthDate());
        assertEquals("M", output.gender());
        assertEquals("en-US", output.preferredLanguage());
        assertEquals("USD", output.preferredCurrency());
        assertTrue(output.newsletterSubscribed());

        verify(transactionManager).execute(any());
        verify(profileGateway).update(profile);
    }

    @Test
    @DisplayName("Deve retornar erro quando user id for nulo")
    void givenNullUserId_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(validCommand(null));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(profileGateway);
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando user id estiver em branco")
    void givenBlankUserId_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(validCommand("   "));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(profileGateway);
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando user id nao for UUID")
    void givenInvalidUserId_whenExecute_thenReturnUserNotFound() {
        final var result = useCase.execute(validCommand("not-a-uuid"));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), UserError.USER_NOT_FOUND.message());
        verifyNoInteractions(profileGateway);
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando perfil nao for encontrado")
    void givenMissingProfile_whenExecute_thenReturnProfileNotFound() {
        final var userId = UserId.unique();
        when(profileGateway.findByUserId(userId)).thenReturn(Optional.empty());

        final var result = useCase.execute(validCommand(userId.getValue().toString()));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), ProfileError.PROFILE_NOT_FOUND.message());
        verify(profileGateway, never()).update(any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando CPF ja pertencer a outro usuario")
    void givenCpfAlreadyInUse_whenExecute_thenReturnCpfAlreadyInUse() {
        final var userId = UserId.unique();
        final var profile = buildProfile(userId);

        when(profileGateway.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileGateway.existsByCpfAndNotUserId("529.982.247-25", userId)).thenReturn(true);

        final var result = useCase.execute(validCommand(userId.getValue().toString()));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), ProfileError.CPF_ALREADY_IN_USE.message());
        verify(profileGateway, never()).update(any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve normalizar CPF em branco para nulo sem consultar unicidade")
    void givenBlankCpf_whenExecute_thenPersistNullCpf() {
        final var userId = UserId.unique();
        final var profile = buildProfile(userId);

        when(profileGateway.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileGateway.update(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final var result = useCase.execute(new UpdateProfileCommand(
                userId.getValue().toString(),
                "Ana",
                "Lima",
                "   ",
                LocalDate.parse("1995-01-10"),
                "F",
                "pt-BR",
                "BRL",
                false
        ));

        assertTrue(result.isRight());
        assertNull(result.get().cpf());
        verify(profileGateway, never()).existsByCpfAndNotUserId(any(), any());
        verify(profileGateway).update(profile);
    }

    @Test
    @DisplayName("Deve retornar erro quando dados violarem invariantes do perfil")
    void givenInvalidProfileData_whenExecute_thenReturnValidationError() {
        final var userId = UserId.unique();
        final var profile = buildProfile(userId);

        when(profileGateway.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileGateway.existsByCpfAndNotUserId("000.000.000-00", userId)).thenReturn(false);

        final var result = useCase.execute(new UpdateProfileCommand(
                userId.getValue().toString(),
                "Bruno",
                "Dias",
                "000.000.000-00",
                LocalDate.parse("1992-04-18"),
                "M",
                "p",
                "US",
                true
        ));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), ProfileError.CPF_INVALID.message());
        assertError(result.getLeft(), ProfileError.PREFERRED_LANGUAGE_INVALID.message());
        assertError(result.getLeft(), ProfileError.PREFERRED_CURRENCY_INVALID.message());
        verify(profileGateway, never()).update(any());
        verify(transactionManager, never()).execute(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando transacao falhar")
    void givenTransactionFailure_whenExecute_thenReturnNotification() {
        final var userId = UserId.unique();
        final var profile = buildProfile(userId);

        when(profileGateway.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileGateway.existsByCpfAndNotUserId("529.982.247-25", userId)).thenReturn(false);
        doThrow(new IllegalStateException("db down")).when(transactionManager).execute(any());

        final var result = useCase.execute(validCommand(userId.getValue().toString()));

        assertTrue(result.isLeft());
        assertFalse(result.getLeft().getErrors().isEmpty());
        verify(profileGateway, never()).update(any());
    }

    private UpdateProfileCommand validCommand(final String userId) {
        return new UpdateProfileCommand(
                userId,
                "Bruno",
                "Dias",
                "529.982.247-25",
                LocalDate.parse("1992-04-18"),
                "M",
                "pt-BR",
                "BRL",
                true
        );
    }

    private Profile buildProfile(final UserId userId) {
        final var now = Instant.parse("2026-04-18T12:00:00Z");
        return Profile.with(
                ProfileId.unique(),
                userId,
                "John",
                "Doe",
                "John Doe",
                "https://cdn.example.com/avatar.png",
                LocalDate.parse("1990-01-01"),
                "M",
                null,
                "pt-BR",
                "BRL",
                false,
                now,
                now,
                now,
                now,
                null
        );
    }

    private void assertError(final com.btree.shared.validation.Notification notification, final String message) {
        assertTrue(notification.getErrors().stream().anyMatch(error -> error.message().equals(message)));
    }
}
