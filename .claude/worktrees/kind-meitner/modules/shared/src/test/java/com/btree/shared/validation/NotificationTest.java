package com.btree.shared.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para o Notification Pattern")
class NotificationTest {

    @Test
    @DisplayName("Deve criar uma notificacao vazia e sem erros")
    void deveCriarNotificacaoVazia() {
        final var notification = Notification.create();
        
        assertNotNull(notification);
        assertTrue(notification.getErrors().isEmpty());
        assertFalse(notification.hasError()); // Assumindo que hasError existe na interface default
    }

    @Test
    @DisplayName("Deve acumular erros na mesma notificacao")
    void deveAcumularErros() {
        final var notification = Notification.create();
        
        notification.append(new Error("Erro 1"));
        notification.append(new Error("Erro 2"));

        assertTrue(notification.hasError());
        assertEquals(2, notification.getErrors().size());
        assertEquals("Erro 1", notification.firstError().message());
    }

    @Test
    @DisplayName("Deve inicializar uma notificacao a partir de uma excecao ou erro singular")
    void deveCriarNotificacaoComErro() {
        final var notif1 = Notification.create(new Error("Deu ruim"));
        assertTrue(notif1.hasError());
        assertEquals(1, notif1.getErrors().size());

        final var notif2 = Notification.create(new IllegalArgumentException("Falha de negocio"));
        assertTrue(notif2.hasError());
        assertEquals("Falha de negocio", notif2.firstError().message());
    }

    @Test
    @DisplayName("Deve copiar os erros de outro validation handler")
    void deveFazerAppendDeOutroHandler() {
        final var notif1 = Notification.create(new Error("Erro do handler 1"));
        final var notif2 = Notification.create();
        
        notif2.append(notif1);
        
        assertEquals(1, notif2.getErrors().size());
        assertEquals("Erro do handler 1", notif2.firstError().message());
    }
}
