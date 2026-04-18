package com.btree.shared.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para o Value Object PhoneNumber")
class PhoneNumberTest {

    @Test
    @DisplayName("Deve criar telefone num formato nacional válido DDI DDD e numero")
    void deveCriarTelefoneValido() {
        final var phone = PhoneNumber.of("+55 11 99999-9999");
        assertNotNull(phone);
        assertEquals("+55 11 99999-9999", phone.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+5511999999999",
            "(11) 99999-9999",
            "11 9999-9999",
            "11999999999"
    })
    @DisplayName("Deve aceitar diversos formatos validos comuns em telefones brasileiros")
    void deveAceitarFormatacoesComuns(String valor) {
        final var phone = PhoneNumber.of(valor);
        assertNotNull(phone);
        assertEquals(valor, phone.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+55 a11 9999", // contem letras
            "(11) ", // sem hifen e numeros longos
            "---", // só caracteres especiais
            "123", // muito curto (menos de 7 digitos)
    })
    @DisplayName("Deve lançar exceção para telefones inválidos e muito curtos")
    void deveLancarExcecaoTelefoneInvalido(String valor) {
        assertThrows(IllegalArgumentException.class, () -> PhoneNumber.of(valor));
    }

    @Test
    @DisplayName("Deve lançar exceção para null ou vazio")
    void deveLancarExcecaoNuloOuVazio() {
        assertThrows(NullPointerException.class, () -> PhoneNumber.of(null));
        assertThrows(IllegalArgumentException.class, () -> PhoneNumber.of("   "));
        assertThrows(IllegalArgumentException.class, () -> PhoneNumber.of(""));
    }
}
