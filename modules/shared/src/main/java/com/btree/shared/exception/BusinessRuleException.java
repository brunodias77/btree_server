package com.btree.shared.exception;

import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Error;

import java.util.List;

/**
 * Exceção para violação de regra de negócio.
 * <p>
 * Utilizada quando uma operação viola uma invariante ou pré-condição
 * definida pelas regras de domínio.
 */
public class BusinessRuleException extends DomainException {

    protected BusinessRuleException(final String message) {
        super(message, List.of(new Error(message)));
    }

    protected BusinessRuleException(final List<Error> errors) {
        super(errors);
    }

    public static BusinessRuleException with(final String message) {
        return new BusinessRuleException(message);
    }

    public static BusinessRuleException with(final List<Error> errors) {
        return new BusinessRuleException(errors);
    }
}
