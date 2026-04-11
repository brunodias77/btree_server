package com.btree.domain.user.validator;

import com.btree.domain.user.entity.User;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do UserValidator acoplado a Entidade User")
public class UserValidatorTest {

    @Test
    @DisplayName("Deve validar a entidade perfeitamente sem falhas")
    void deveValidarComSucesso() {
        final var notif = Notification.create();
        
        assertDoesNotThrow(() -> {
            User.create("bruno_dias", "bruno@mail.com", "Senh@Forte123", notif);
        });
        
        assertFalse(notif.hasError());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bruno dias!", "br@uno", " b r u n o "})
    @DisplayName("Deve retornar erros ao passar username invalido e estourar a DomainException imediatamente")
    void deveRetornarErroUsername(String invalidUsername) {
        final var notif = Notification.create();
        
        final var exception = assertThrows(DomainException.class, () -> {
            User.create(invalidUsername, "bruno@mail.com", "Senh@Forte123", notif);
        });

        assertTrue(exception.getErrors().stream().anyMatch(e -> e.message().contains("'username' de formato inválido")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "abc.com", "@dominio", "abc@dominio"})
    @DisplayName("Deve retornar erros ao passar email invalido")
    void deveRetornarErroEmail(String emailInvalido) {
        final var notif = Notification.create();

        final var exception = assertThrows(DomainException.class, () -> {
            User.create("brunodias", emailInvalido, "Senh@Forte123", notif);
        });

        assertTrue(exception.getErrors().stream().anyMatch(e -> e.message().contains("email")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fraca", "SEM_NUMERO", "12345678a", "tudo_minusculo1", "TUDO_MAIUSCULO2"})
    @DisplayName("Deve retornar erro ao passar senha fora dos padroes de seguranca")
    void deveRetornarErroSenha(String senhaInvalida) {
        final var notif = Notification.create();

        final var exception = assertThrows(DomainException.class, () -> {
            User.create("brunodias", "bruno@mail.com", senhaInvalida, notif);
        });

        assertTrue(exception.getErrors().stream().anyMatch(e -> e.message().contains("password")));
    }
}
