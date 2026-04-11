package com.btree.shared.valueobject;




import com.btree.shared.domain.ValueObject;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object para número de telefone.
 * <p>
 * Mapeado para {@code users.users.phone_number VARCHAR(50)}.
 * Aceita formatos nacionais e internacionais.
 */
public class PhoneNumber extends ValueObject {

    /**
     * Aceita telefones nos formatos:
     * +55 11 99999-9999, (11) 99999-9999, 11999999999, +5511999999999
     * Exige no mínimo 7 dígitos numéricos para rejeitar strings sem números como "(---)".
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[0-9\\s\\-().]{7,50}$"
    );

    private final String value;

    private PhoneNumber(final String value) {
        Objects.requireNonNull(value, "'phoneNumber' não pode ser nulo");
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("'phoneNumber' não pode ser vazio");
        }
        final String digitsOnly = trimmed.replaceAll("[^\\d]", "");
        if (!PHONE_PATTERN.matcher(trimmed).matches() || digitsOnly.length() < 7) {
            throw new IllegalArgumentException("Formato de telefone inválido: '%s'".formatted(value));
        }
        this.value = trimmed;
    }

    public static PhoneNumber of(final String value) {
        return new PhoneNumber(value);
    }

    public String getValue() {
        return value;
    }

    /**
     * Retorna apenas os dígitos do telefone.
     */
    public String getDigits() {
        return value.replaceAll("[^\\d]", "");
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PhoneNumber that = (PhoneNumber) o;
        return getDigits().equals(that.getDigits());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDigits());
    }

    @Override
    public String toString() {
        return value;
    }
}
