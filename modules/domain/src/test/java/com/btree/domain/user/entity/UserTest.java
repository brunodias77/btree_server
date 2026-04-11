package com.btree.domain.user.entity;

import com.btree.domain.UnitTest;
import com.btree.domain.user.event.UserCreatedEvent;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da Entidade User (Aggregate Root)")
public class UserTest extends UnitTest {

    @Test
    @DisplayName("Deve instanciar a entidade User através do método create e disparar o evento de criação")
    void deveCriarUsuarioDisparandoEvento() {
        // Arrange
        final var expectedUsername = "bruno_dias";
        final var expectedEmail = "bruno@email.com";
        final var expectedPasswordHash = "Hashforte123";

        // Act
        final var actualNotification = Notification.create();
        final var actualUser = User.create(expectedUsername, expectedEmail, expectedPasswordHash, actualNotification);

        // Assert
        assertNotNull(actualUser);
        assertNotNull(actualUser.getId());
        assertFalse(actualNotification.hasError());

        // Validating properties
        assertEquals(expectedUsername, actualUser.getUsername());
        assertEquals(expectedEmail, actualUser.getEmail());
        assertEquals(expectedPasswordHash, actualUser.getPasswordHash());
        assertFalse(actualUser.isEmailVerified());
        assertFalse(actualUser.isPhoneNumberVerified());
        assertFalse(actualUser.isTwoFactorEnabled());
        assertFalse(actualUser.isAccountLocked());
        assertTrue(actualUser.isEnabled());

        // Validating Nested Entities created automatically
        assertNotNull(actualUser.getProfile());

        assertNotNull(actualUser.getNotificationPreference());

        // Validating Domain Events (UserCreatedEvent)
        final var domainEvents = actualUser.getDomainEvents();
        assertEquals(1, domainEvents.size());
        
        final var event = (UserCreatedEvent) domainEvents.get(0);
        assertEquals(actualUser.getId().getValue().toString(), event.getUserId());
        assertEquals(expectedUsername, event.getUsername());
        assertEquals(expectedEmail, event.getEmail());
    }
}
