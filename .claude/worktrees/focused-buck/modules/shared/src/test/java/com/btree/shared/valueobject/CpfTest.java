package com.btree.shared.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para o Value Object Cpf")
class CpfTest {

    @Test
    @DisplayName("Deve instanciar um CPF perfeitamente válido formatado")
    void deveInstanciarCpfValidoFormatado() {
        final var cpf = Cpf.of("123.456.789-09");
        assertNotNull(cpf);
        assertEquals("123.456.789-09", cpf.getValue());
        assertEquals("12345678909", cpf.getDigits());
    }

    @Test
    @DisplayName("Deve criar e formatar automaticamente um CPF válido não formatado")
    void deveCriarEFormatarAutomaticamenteCpfNaoFormatado() {
        final var cpf = Cpf.ofUnformatted("12345678909");
        assertNotNull(cpf);
        assertEquals("123.456.789-09", cpf.getValue());
        assertEquals("12345678909", cpf.getDigits());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "123456789-09", // sem pontos suficientes
            "123.456.78909", // sem hifen
            "12.456.789-09", // faltam digitos no começo
            "123.456.789-9", // falta digito no fim
            "ABC.DEF.GHI-JK" // contem letras
    })
    @DisplayName("Deve levantar exceção quando o formato for puramente inválido")
    void deveLevantarExcecaoQuandoFormatoInvalido(String valor) {
        assertThrows(IllegalArgumentException.class, () -> Cpf.of(valor));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "111.111.111-11",
            "222.222.222-22",
            "000.000.000-00",
            "123.456.789-00", // digitos verificadores totalmente incorretos (matemática)
            "442.274.650-80" // digito 80 falso
    })
    @DisplayName("Deve levantar exceção ao instanciar CPF com dígitos ou verificação matematicamente inválidos")
    void deveLevantarExcecaoQuandoCpfMatematicamenteInvalido(String valor) {
        assertThrows(IllegalArgumentException.class, () -> Cpf.of(valor), "CPF inválido");
    }

    @Test
    @DisplayName("Deve levantar exceção se passar valor nulo")
    void deveLancarExcecaoParaNulo() {
        assertThrows(NullPointerException.class, () -> Cpf.of(null));
        assertThrows(NullPointerException.class, () -> Cpf.ofUnformatted(null));
    }

    @Test
    @DisplayName("Deve garantir a igualdade entre objetos de valor iguais")
    void deveGarantirIgualdade() {
        final var cpf1 = Cpf.of("123.456.789-09");
        final var cpf2 = Cpf.ofUnformatted("12345678909");
        assertEquals(cpf1, cpf2);
        assertEquals(cpf1.hashCode(), cpf2.hashCode());
    }
}
