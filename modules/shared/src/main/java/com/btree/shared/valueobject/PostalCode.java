package com.btree.shared.valueobject;


import com.btree.shared.domain.ValueObject;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object para CEP (Código de Endereçamento Postal).
 * <p>
 * Mapeado para {@code users.addresses.postal_code VARCHAR(9)}.
 * Aceita formatos {@code XXXXX-XXX} e {@code XXXXXXXX}
 * (consistente com CHECK constraint {@code chk_addresses_postal_code}).
 */
public class PostalCode extends ValueObject {

    private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("^\\d{5}-?\\d{3}$");

    private final String value;

    private PostalCode(final String value) {
        Objects.requireNonNull(value, "'postalCode' não pode ser nulo");
        final String trimmed = value.trim();
        if (!POSTAL_CODE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Formato de CEP inválido: '%s'. Esperado: XXXXX-XXX ou XXXXXXXX".formatted(value));
        }
        this.value = trimmed;
    }

    public static PostalCode of(final String value) {
        return new PostalCode(value);
    }

    public String getValue() {
        return value;
    }

    /**
     * Retorna o CEP formatado com hífen: {@code XXXXX-XXX}.
     */
    public String formatted() {
        final String digits = value.replaceAll("[^\\d]", "");
        return "%s-%s".formatted(digits.substring(0, 5), digits.substring(5));
    }

    /**
     * Retorna apenas os dígitos do CEP.
     */
    public String getDigits() {
        return value.replaceAll("[^\\d]", "");
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PostalCode that = (PostalCode) o;
        return getDigits().equals(that.getDigits());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDigits());
    }

    @Override
    public String toString() {
        return formatted();
    }
}
