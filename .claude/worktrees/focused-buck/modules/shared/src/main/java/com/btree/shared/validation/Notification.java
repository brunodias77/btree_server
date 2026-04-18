package com.btree.shared.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Notification implements ValidationHandler {

    private final List<Error> errors = new ArrayList<>();

    public static Notification create() { return new Notification(); }

    public static Notification create(Error error) {
        return new Notification().append(error);
    }

    public static Notification create(final Throwable t) {
        return create(new Error(t.getMessage()));
    }

    @Override
    public Notification append(Error error) {
        if (error != null) {
            this.errors.add(error);
        }
        return this;
    }

    @Override
    public Notification append(ValidationHandler handler) {
        this.errors.addAll(handler.getErrors());
        return this;
    }

    @Override
    public List<Error> getErrors() { return Collections.unmodifiableList(this.errors); }
}