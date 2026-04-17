package com.btree.application.usecase.user.get_current_user;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.entity.User;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.identifier.ProfileId;
import com.btree.domain.user.identifier.UserId;
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
@DisplayName("Get current user use case")
class GetCurrentUserUseCaseTest {

    @Mock UserGateway userGateway;

    @Test
    @DisplayName("Deve retornar dados do usuario autenticado")
    void givenExistingUser_whenExecute_thenReturnCurrentUser() {
        final var userId = UserId.unique();
        final var createdAt = Instant.parse("2026-04-15T01:00:00Z");
        final var profile = buildProfile(userId);
        final var user = buildUser(userId, createdAt, profile);
        user.addRole("customer");
        user.addRole("admin");

        when(userGateway.findById(userId)).thenReturn(Optional.of(user));

        final var useCase = new GetCurrentUserUseCase(userGateway);
        final var result = useCase.execute(new GetCurrentUserInput(userId.getValue().toString()));

        assertTrue(result.isRight());
        final var output = result.get();
        assertEquals(userId.getValue().toString(), output.id());
        assertEquals("johndoe", output.username());
        assertEquals("john@example.com", output.email());
        assertTrue(output.emailVerified());
        assertTrue(output.roles().contains("customer"));
        assertTrue(output.roles().contains("admin"));
        assertEquals(createdAt, output.createdAt());
        assertNotNull(output.profile());
        assertEquals("John", output.profile().firstName());
        assertEquals("Doe", output.profile().lastName());
        assertEquals("John Doe", output.profile().displayName());
        assertEquals("https://cdn.example.com/avatar.png", output.profile().avatarUrl());
        assertEquals("pt-BR", output.profile().preferredLanguage());
        assertEquals("BRL", output.profile().preferredCurrency());
    }

    @Test
    @DisplayName("Deve retornar usuario com profile nulo quando usuario nao tiver profile")
    void givenExistingUserWithoutProfile_whenExecute_thenReturnNullProfile() {
        final var userId = UserId.unique();
        final var user = buildUser(userId, Instant.parse("2026-04-15T01:00:00Z"), null);

        when(userGateway.findById(userId)).thenReturn(Optional.of(user));

        final var useCase = new GetCurrentUserUseCase(userGateway);
        final var result = useCase.execute(new GetCurrentUserInput(userId.getValue().toString()));

        assertTrue(result.isRight());
        assertNull(result.get().profile());
    }

    @Test
    @DisplayName("Deve retornar erro quando usuario nao for encontrado")
    void givenUserNotFound_whenExecute_thenReturnUserNotFoundError() {
        final var userId = UserId.unique();
        when(userGateway.findById(userId)).thenReturn(Optional.empty());

        final var useCase = new GetCurrentUserUseCase(userGateway);
        final var result = useCase.execute(new GetCurrentUserInput(userId.getValue().toString()));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.USER_NOT_FOUND.message())));
    }

    @Test
    @DisplayName("Deve retornar erro quando user id for nulo")
    void givenNullUserId_whenExecute_thenReturnUserNotFoundError() {
        final var useCase = new GetCurrentUserUseCase(userGateway);
        final var result = useCase.execute(new GetCurrentUserInput(null));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.USER_NOT_FOUND.message())));
        verify(userGateway, never()).findById(any());
    }

    @Test
    @DisplayName("Deve retornar erro quando user id estiver em branco")
    void givenBlankUserId_whenExecute_thenReturnUserNotFoundError() {
        final var useCase = new GetCurrentUserUseCase(userGateway);
        final var result = useCase.execute(new GetCurrentUserInput("   "));

        assertTrue(result.isLeft());
        assertTrue(result.getLeft().getErrors().stream()
                .anyMatch(e -> e.message().equals(UserError.USER_NOT_FOUND.message())));
        verify(userGateway, never()).findById(any());
    }

    @Test
    @DisplayName("Deve lancar erro quando user id nao for UUID valido")
    void givenInvalidUuid_whenExecute_thenThrowIllegalArgument() {
        final var useCase = new GetCurrentUserUseCase(userGateway);

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(new GetCurrentUserInput("not-a-uuid")));
        verify(userGateway, never()).findById(any());
    }

    private User buildUser(final UserId userId, final Instant createdAt, final Profile profile) {
        return User.with(
                userId, "johndoe", "john@example.com",
                true, "hashed-pw",
                null, false, false, null,
                false, null, 0, true,
                createdAt, createdAt, 0, profile, null
        );
    }

    private Profile buildProfile(final UserId userId) {
        final var now = Instant.parse("2026-04-15T01:00:00Z");
        return Profile.with(
                ProfileId.unique(),
                userId,
                "John",
                "Doe",
                "John Doe",
                "https://cdn.example.com/avatar.png",
                LocalDate.parse("1990-01-01"),
                "male",
                "12345678900",
                "pt-BR",
                "BRL",
                true,
                now,
                now,
                now,
                now,
                null
        );
    }
}
