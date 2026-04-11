package com.btree.shared.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes para o Value Object Money")
class MoneyTest {

    @Test
    @DisplayName("Deve instanciar um Money com valor positivo em BigDecimal e arredondar 2 casas")
    void deveInstanciarMoneyPositivo() {
        final var money = Money.of(new BigDecimal("10.555"));
        assertNotNull(money);
        // O RoundingMode.HALF_UP de 10.555 vai para 10.56
        assertEquals(new BigDecimal("10.56"), money.getAmount());
        assertEquals("BRL", money.getCurrency());
    }

    @Test
    @DisplayName("Deve instanciar um Money com valor positivo usando double")
    void deveInstanciarMoneyComDouble() {
        final var money = Money.of(25.99);
        assertNotNull(money);
        assertEquals(new BigDecimal("25.99"), money.getAmount());
    }

    @Test
    @DisplayName("Deve levantar exceção caso tente instanciar um valor negativo")
    void deveRejeitarValoresNegaticos() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(-5.0));
        assertThrows(IllegalArgumentException.class, () -> Money.of(new BigDecimal("-0.01")));
    }

    @Test
    @DisplayName("Deve instanciar constantes Zero corretamente")
    void deveCriarConstantsZero() {
        final var zero1 = Money.zero();
        final var zero2 = Money.of(0);

        assertEquals(new BigDecimal("0.00"), zero1.getAmount());
        assertEquals(zero1, zero2);
    }

    @Test
    @DisplayName("Soma de valores com metodo add deve retornar uma nova instancia de Money somada")
    void deveSomarValores() {
        final var m1 = Money.of(10.50);
        final var m2 = Money.of(15.20);
        
        final var result = m1.add(m2);
        assertEquals(new BigDecimal("25.70"), result.getAmount());
    }

    @Test
    @DisplayName("Subtracao de valores não pode resultar em valor negativo")
    void deveSubtrairValoresSemFicarNegativo() {
        final var m1 = Money.of(100.0);
        final var m2 = Money.of(40.0);
        
        final var resultado = m1.subtract(m2);
        assertEquals(new BigDecimal("60.00"), resultado.getAmount());
        
        // Se subtrair mais, lança excecao pois não pode haver dinheiro negativo
        assertThrows(IllegalArgumentException.class, () -> m2.subtract(m1));
    }
}
