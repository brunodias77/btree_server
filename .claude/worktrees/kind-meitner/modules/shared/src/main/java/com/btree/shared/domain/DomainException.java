package com.btree.shared.domain;

import com.btree.shared.validation.Error;

import java.util.Collections;
import java.util.List;

/**
 * Exceção base do domínio.
 * <p>
 * Todas as exceções de negócio estendem esta classe, carregando
 * uma lista de {@link Error} com mensagens descritivas.
 */
public class DomainException extends RuntimeException {

    private final List<Error> errors;

    protected DomainException(final String message, final List<Error> errors) {
        super(message);
        this.errors = errors != null ? Collections.unmodifiableList(errors) : List.of();
    }

    public DomainException(final List<Error> errors) {
        this(errors != null && !errors.isEmpty() ? errors.get(0).message() : "", errors);
    }

    public static DomainException with(final Error error) {
        return new DomainException(List.of(error));
    }

    public static DomainException with(final List<Error> errors) {
        return new DomainException(errors);
    }

    public List<Error> getErrors() {
        return errors;
    }
}

