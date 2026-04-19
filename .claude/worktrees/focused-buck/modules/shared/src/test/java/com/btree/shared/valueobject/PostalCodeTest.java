package com.btree.shared.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para o Value Object PostalCode")
class PostalCodeTest {

    @Test
    @DisplayName("Deve instanciar um PostalCode com formato ja com hifen")
    void deveInstanciarCepComHifen() {
        final var postalCode = PostalCode.of("12345-678");
        assertNotNull(postalCode);
        assertEquals("12345-678", postalCode.getValue());
        assertEquals("12345678", postalCode.getDigits());
    }

    @Test
    @DisplayName("Deve instanciar um PostalCode com formato apenas de digitos")
    void deveInstanciarCepSoDigitos() {
        final var postalCode = PostalCode.of("12345678");
        assertNotNull(postalCode);
        assertEquals("12345678", postalCode.getValue());
        assertEquals("12345-678", postalCode.formatted());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234-5678",
            "1234567", // falta 1 digito
            "123456789", // sobra 1 digito
            "ABCDE-FGH", // contem letras
            "12345-6789" // final com 4 digitos
    })
    @DisplayName("Deve lançar exceção para CEPs inválidos")
    void deveLancarExcecaoParaCepInvalido(String valor) {
        assertThrows(IllegalArgumentException.class, () -> PostalCode.of(valor));
    }

    @Test
    @DisplayName("Deve lançar exceção se CEP for nulo")
    void deveLancarExcecaoParaNulo() {
        assertThrows(NullPointerException.class, () -> PostalCode.of(null));
    }
}
