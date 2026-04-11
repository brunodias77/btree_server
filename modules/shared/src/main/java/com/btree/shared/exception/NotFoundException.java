package com.btree.shared.exception;

import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Error;

import java.util.List;
import java.util.UUID;

/**
 * Exceção para recurso não encontrado (HTTP 404 semântico).
 * <p>
 * Possui factory methods para gerar mensagens padronizadas
 * baseadas no tipo da entidade e seu identificador.
 */
public class NotFoundException extends DomainException {

    protected NotFoundException(final String message) {
        super(message, List.of(new Error(message)));
    }

    public static NotFoundException with(final Class<?> entityClass, final UUID id) {
        final String message = "%s com ID '%s' não foi encontrado"
                .formatted(entityClass.getSimpleName(), id);
        return new NotFoundException(message);
    }

    public static NotFoundException with(final Class<?> entityClass, final String identifier) {
        final String message = "%s '%s' não foi encontrado"
                .formatted(entityClass.getSimpleName(), identifier);
        return new NotFoundException(message);
    }

    public static NotFoundException with(final String message) {
        return new NotFoundException(message);
    }
}
