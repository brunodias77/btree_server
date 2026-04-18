package com.btree.shared.validation;



import com.btree.shared.domain.DomainException;

import java.util.List;

import com.btree.shared.validation.Error;


public class ThrowsValidationHandler implements ValidationHandler {

    @Override
    public ValidationHandler append(Error error) {
        throw new DomainException(List.of(error));
    }

    @Override
    public ValidationHandler append(ValidationHandler handler) {
        throw new DomainException(handler.getErrors());
    }

    @Override
    public List<Error> getErrors() { return List.of(); }
}
