package com.btree.shared.valueobject;



import com.btree.shared.domain.ValueObject;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object para CPF (Cadastro de Pessoa Física).
 * <p>
 * Mapeado para {@code users.profiles.cpf VARCHAR(14)}.
 * Formato esperado: {@code XXX.XXX.XXX-XX}
 * (consistente com CHECK constraint {@code chk_profiles_cpf_format}).
 */
public class Cpf extends ValueObject {

    private static final Pattern CPF_FORMAT = Pattern.compile("^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$");

    private final String value;

    private Cpf(final String value) {
        Objects.requireNonNull(value, "'cpf' não pode ser nulo");
        final String trimmed = value.trim();
        if (!CPF_FORMAT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Formato de CPF inválido: '%s'. Esperado: XXX.XXX.XXX-XX".formatted(value));
        }
        if (!isValidCpf(trimmed)) {
            throw new IllegalArgumentException("CPF inválido: '%s'".formatted(value));
        }
        this.value = trimmed;
    }

    public static Cpf of(final String value) {
        return new Cpf(value);
    }

    /**
     * Cria um CPF a partir de apenas dígitos (11 dígitos), formatando automaticamente.
     */
    public static Cpf ofUnformatted(final String digits) {
        Objects.requireNonNull(digits, "'cpf' não pode ser nulo");
        final String clean = digits.replaceAll("[^\\d]", "");
        if (clean.length() != 11) {
            throw new IllegalArgumentException("CPF deve ter 11 dígitos");
        }
        final String formatted = "%s.%s.%s-%s".formatted(
                clean.substring(0, 3),
                clean.substring(3, 6),
                clean.substring(6, 9),
                clean.substring(9, 11)
        );
        return new Cpf(formatted);
    }

    public String getValue() {
        return value;
    }

    /**
     * Retorna apenas os dígitos do CPF (sem pontos e hífen).
     */
    public String getDigits() {
        return value.replaceAll("[^\\d]", "");
    }

    private static boolean isValidCpf(final String formattedCpf) {
        final String digits = formattedCpf.replaceAll("[^\\d]", "");
        if (digits.length() != 11) return false;

        // Rejeita CPFs com todos os dígitos iguais
        if (digits.chars().distinct().count() == 1) return false;

        // Validação dos dígitos verificadores
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * (10 - i);
        }
        int firstVerifier = 11 - (sum % 11);
        if (firstVerifier >= 10) firstVerifier = 0;
        if (Character.getNumericValue(digits.charAt(9)) != firstVerifier) return false;

        sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * (11 - i);
        }
        int secondVerifier = 11 - (sum % 11);
        if (secondVerifier >= 10) secondVerifier = 0;
        return Character.getNumericValue(digits.charAt(10)) == secondVerifier;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Cpf cpf = (Cpf) o;
        return value.equals(cpf.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
