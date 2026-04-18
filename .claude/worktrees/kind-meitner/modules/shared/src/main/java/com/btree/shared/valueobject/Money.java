package com.btree.shared.valueobject;




import com.btree.shared.domain.ValueObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object para representação monetária.
 * <p>
 * Mapeado para colunas {@code DECIMAL(10,2)} do schema (price, amount, subtotal, total, etc.).
 * Sempre arredondado para 2 casas decimais usando {@link RoundingMode#HALF_UP}.
 */
public class Money extends ValueObject {

    public static final String DEFAULT_CURRENCY = "BRL";
    public static final Money ZERO = new Money(BigDecimal.ZERO, DEFAULT_CURRENCY);

    private final BigDecimal amount;
    private final String currency;

    private Money(final BigDecimal amount, final String currency) {
        Objects.requireNonNull(amount, "'amount' não pode ser nulo");
        Objects.requireNonNull(currency, "'currency' não pode ser nulo");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("'amount' não pode ser negativo");
        }
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public static Money of(final BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    public static Money of(final BigDecimal amount, final String currency) {
        return new Money(amount, currency);
    }

    public static Money of(final double amount) {
        return new Money(BigDecimal.valueOf(amount), DEFAULT_CURRENCY);
    }

    public static Money zero() {
        return ZERO;
    }

    public Money add(final Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtrai {@code other} deste valor monetário.
     *
     * @param other valor a subtrair (mesma moeda)
     * @return novo {@code Money} com o resultado
     * @throws IllegalArgumentException se {@code other} tiver moeda diferente
     *                                  ou se o resultado for negativo (use
     *                                  {@link #isGreaterThanOrEqual} para verificar antes)
     */
    public Money subtract(final Money other) {
        assertSameCurrency(other);
        final BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Resultado da subtração não pode ser negativo");
        }
        return new Money(result, this.currency);
    }

    public Money multiply(final int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("'quantity' não pode ser negativo");
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }

    public Money multiply(final BigDecimal factor) {
        Objects.requireNonNull(factor, "'factor' não pode ser nulo");
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public boolean isGreaterThan(final Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(final Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    private void assertSameCurrency(final Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Moedas diferentes: '%s' e '%s'".formatted(this.currency, other.currency));
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Money money = (Money) o;
        return amount.compareTo(money.amount) == 0 && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return "%s %s".formatted(currency, amount.toPlainString());
    }
}
