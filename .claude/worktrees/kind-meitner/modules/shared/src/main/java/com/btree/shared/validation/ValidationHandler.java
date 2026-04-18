package com.btree.shared.validation;


import java.util.List;


public interface ValidationHandler {
    ValidationHandler append(Error error);
    ValidationHandler append(ValidationHandler handler);
    List<Error> getErrors();

    default boolean hasError() {
        return getErrors() != null && !getErrors().isEmpty();
    }

    default Error firstError() {
        List<Error> errors = getErrors();
        return (errors != null && !errors.isEmpty()) ? errors.get(0) : null;
    }
}
