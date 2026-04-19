package com.btree.shared.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para o Value Object Email")
class EmailTest {

    @Test
    @DisplayName("Deve criar um Email válido a partir de string bem formatada")
    void deveCriarEmailValido() {
        final var email = Email.of("usuario.teste@dominio.com.br");
        assertNotNull(email);
        assertEquals("usuario.teste@dominio.com.br", email.getValue());
    }

    @Test
    @DisplayName("Deve criar Email fazendo o trim e passando para minúsculo automaticamente")
    void deveTratarStringAntesDeValidar() {
        final var email = Email.of("   USUARIO.teste@DOMINIO.com  ");
        assertNotNull(email);
        assertEquals("usuario.teste@dominio.com", email.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "usuario",
            "usuario@",
            "usuario@dominio",
            "@dominio.com",
            "usuario.dominio.com",
            "usuario@dominio.",
            "usuario@.com"
    })
    @DisplayName("Deve lançar exceção ao passar formatos inválidos de e-mail")
    void deveLancarExcecaoQuandoFormatoInvalido(String valor) {
        final var ex = assertThrows(IllegalArgumentException.class, () -> Email.of(valor));
        assertTrue(ex.getMessage().contains("Formato de e-mail inválido"));
    }

    @Test
    @DisplayName("Deve lançar exceção para email nulo ou vazio")
    void deveLancarExcecaoParaNuloOuVazio() {
        assertThrows(NullPointerException.class, () -> Email.of(null));
        assertThrows(IllegalArgumentException.class, () -> Email.of(""));
        assertThrows(IllegalArgumentException.class, () -> Email.of("   "));
    }

    @Test
    @DisplayName("Deve lançar exceção ao passar email com mais de 256 caracteres")
    void deveLancarExcecaoQunadoSuperaTamanhoMaximo() {
        String longEmail = "a".repeat(250) + "@teste.com";
        assertThrows(IllegalArgumentException.class, () -> Email.of(longEmail));
    }

    @Test
    @DisplayName("Deve garantir a igualdade entre Value Objects de Email")
    void deveGarantirAIgualdade() {
        final var email1 = Email.of("teste@teste.com");
        final var email2 = Email.of("  TESTE@TESTE.COM  ");

        assertEquals(email1, email2);
        assertEquals(email1.hashCode(), email2.hashCode());
    }
}
