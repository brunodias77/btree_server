

package com.btree.shared.valueobject;
import com.btree.shared.domain.ValueObject;
import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object para slug de URL.
 * <p>
 * Mapeado para colunas {@code slug} em {@code catalog.products},
 * {@code catalog.categories} e {@code catalog.brands}.
 * Formato: lowercase, alfanumérico e hifens.
 */
public class Slug extends ValueObject {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s-]");
    private static final Pattern WHITESPACE_OR_HYPHENS = Pattern.compile("[\\s-]+");

    private final String value;

    private Slug(final String value) {
        Objects.requireNonNull(value, "'slug' não pode ser nulo");
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("'slug' não pode ser vazio");
        }
        if (!SLUG_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Formato de slug inválido: '%s'. Use apenas letras minúsculas, números e hifens".formatted(value));
        }
        this.value = trimmed;
    }

    /**
     * Cria um Slug a partir de um valor já formatado.
     */
    public static Slug of(final String value) {
        return new Slug(value);
    }

    /**
     * Gera um slug a partir de um texto livre (ex: nome do produto).
     * <p>
     * Exemplo: "Camiseta Azul Marinho 100% Algodão" → "camiseta-azul-marinho-100-algodao"
     */
    public static Slug slugify(final String text) {
        Objects.requireNonNull(text, "'text' não pode ser nulo");
        if (text.isBlank()) {
            throw new IllegalArgumentException("'text' não pode ser vazio");
        }

        // Normaliza e remove acentos
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");

        // Converte para lowercase
        normalized = normalized.toLowerCase();

        // Remove caracteres não alfanuméricos (exceto espaços e hifens)
        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll("");

        // Substitui espaços e múltiplos hifens por um único hífen
        normalized = WHITESPACE_OR_HYPHENS.matcher(normalized).replaceAll("-");

        // Remove hifens no início e fim
        normalized = normalized.replaceAll("^-+|-+$", "");

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Texto não produziu um slug válido: '%s'".formatted(text));
        }

        return new Slug(normalized);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Slug slug = (Slug) o;
        return value.equals(slug.value);
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
