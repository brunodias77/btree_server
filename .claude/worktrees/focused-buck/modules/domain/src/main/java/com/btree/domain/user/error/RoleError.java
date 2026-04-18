package com.btree.domain.user.error;
import com.btree.shared.validation.Error;

public final class RoleError {
    private RoleError() {}

    public static final Error NAME_EMPTY = new Error("'name' não pode estar vazio");
}
