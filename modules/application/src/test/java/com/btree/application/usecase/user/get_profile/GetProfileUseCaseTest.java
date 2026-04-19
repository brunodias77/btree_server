package com.btree.application.usecase.user.get_profile;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.error.ProfileError;
import com.btree.domain.user.gateway.ProfileGateway;
import com.btree.domain.user.identifier.ProfileId;
import com.btree.domain.user.identifier.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Get profile use case")
class GetProfileUseCaseTest {

    @Mock ProfileGateway profileGateway;

    GetProfileUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProfileUseCase(profileGateway);
    }

    @Test
    @DisplayName("Deve retornar perfil quando usuario existir")
    void givenValidUserId_whenExecute_thenReturnProfile() {
        final var userId = UserId.unique();
        final var profile = buildProfile(userId);

        when(profileGateway.findByUserId(userId)).thenReturn(Optional.of(profile));

        final var result = useCase.execute(new GetProfileCommand(userId.getValue().toString()));

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(profile.getId().getValue().toString(), output.id());
        assertEquals(userId.getValue().toString(), output.userId());
        assertEquals("John", output.firstName());
        assertEquals("Doe", output.lastName());
        assertEquals("John Doe", output.displayName());
        assertEquals("https://cdn.example.com/avatar.png", output.avatarUrl());
        assertEquals(LocalDate.parse("1990-01-01"), output.birthDate());
        assertEquals("M", output.gender());
        assertEquals("529.982.247-25", output.cpf());
        assertEquals("pt-BR", output.preferredLanguage());
        assertEquals("BRL", output.preferredCurrency());
        assertFalse(output.newsletterSubscribed());
        assertNotNull(output.acceptedTermsAt());
        assertNotNull(output.acceptedPrivacyAt());
        assertNotNull(output.createdAt());
        assertNotNull(output.updatedAt());

        verify(profileGateway).findByUserId(userId);
    }

    @Test
    @DisplayName("Deve retornar erro quando user id for nulo")
    void givenNullUserId_whenExecute_thenReturnProfileNotFound() {
        final var result = useCase.execute(new GetProfileCommand(null));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), ProfileError.PROFILE_NOT_FOUND.message());
        verifyNoInteractions(profileGateway);
    }

    @Test
    @DisplayName("Deve retornar erro quando user id estiver em branco")
    void givenBlankUserId_whenExecute_thenReturnProfileNotFound() {
        final var result = useCase.execute(new GetProfileCommand("   "));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), ProfileError.PROFILE_NOT_FOUND.message());
        verifyNoInteractions(profileGateway);
    }

    @Test
    @DisplayName("Deve retornar erro quando perfil nao for encontrado")
    void givenMissingProfile_whenExecute_thenReturnProfileNotFound() {
        final var userId = UserId.unique();
        when(profileGateway.findByUserId(userId)).thenReturn(Optional.empty());

        final var result = useCase.execute(new GetProfileCommand(userId.getValue().toString()));

        assertTrue(result.isLeft());
        assertError(result.getLeft(), ProfileError.PROFILE_NOT_FOUND.message());
        verify(profileGateway).findByUserId(userId);
    }

    @Test
    @DisplayName("Deve lancar excecao quando user id nao for UUID valido")
    void givenInvalidUserId_whenExecute_thenThrowIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(new GetProfileCommand("not-a-uuid"))
        );
        verifyNoInteractions(profileGateway);
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
                "529.982.247-25",
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
