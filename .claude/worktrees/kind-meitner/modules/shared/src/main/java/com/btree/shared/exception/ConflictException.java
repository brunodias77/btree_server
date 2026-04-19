package com.btree.shared.exception;


import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Error;

import java.util.List;

/**
 * Exceção para conflito de estado (HTTP 409 semântico).
 * <p>
 * Utilizada quando uma operação não pode ser concluída porque
 * o recurso já existe ou está em um estado conflitante.
 */
public class ConflictException extends DomainException {

    protected ConflictException(final String message) {
        super(message, List.of(new Error(message)));
    }

    public static ConflictException with(final String message) {
        return new ConflictException(message);
    }

    public static ConflictException with(final Class<?> entityClass, final String field, final String value) {
        final String message = "%s com %s '%s' já existe"
                .formatted(entityClass.getSimpleName(), field, value);
        return new ConflictException(message);
    }
}
