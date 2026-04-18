package com.btree.shared.valueobject;




import com.btree.shared.domain.ValueObject;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object para endereço de e-mail.
 * <p>
 * Mapeado para {@code users.users.email VARCHAR(256)}.
 * Armazenado internamente em lowercase para garantir unicidade
 * (consistente com o index {@code uq_users_email} do schema).
 */
public class Email extends ValueObject {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"
    );

    private final String value;

    private Email(final String value) {
        Objects.requireNonNull(value, "'email' não pode ser nulo");
        final String trimmed = value.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("'email' não pode ser vazio");
        }
        if (trimmed.length() > 256) {
            throw new IllegalArgumentException("'email' não pode exceder 256 caracteres");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Formato de e-mail inválido: '%s'".formatted(value));
        }
        this.value = trimmed;
    }

    public static Email of(final String value) {
        return new Email(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Email email = (Email) o;
        return value.equals(email.value);
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
