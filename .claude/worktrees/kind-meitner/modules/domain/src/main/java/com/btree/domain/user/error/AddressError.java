package com.btree.domain.user.error;

import com.btree.shared.validation.Error;

public final class AddressError {
    private AddressError() {}

    public static final Error STREET_EMPTY            = new Error("'street' não pode estar vazio");
    public static final Error CITY_EMPTY              = new Error("'city' não pode estar vazia");
    public static final Error STATE_INVALID           = new Error("'state' deve conter 2 letras maiúsculas");
    public static final Error POSTAL_CODE_INVALID     = new Error("O formato do 'postalCode' é inválido");
    public static final Error USER_ID_NULL            = new Error("'userId' não pode ser nulo");
    public static final Error COUNTRY_EMPTY           = new Error("'country' não pode estar vazio");
    public static final Error STREET_TOO_LONG         = new Error("'street' deve ter no máximo 255 caracteres");
    public static final Error CITY_TOO_LONG           = new Error("'city' deve ter no máximo 100 caracteres");
    public static final Error ADDRESS_NOT_FOUND              = new Error("Endereço não encontrado");
    public static final Error ADDRESS_ALREADY_DELETED        = new Error("Endereço já foi removido");
    public static final Error ADDRESS_BELONGS_TO_ANOTHER_USER = new Error("Endereço não pertence ao usuário informado");
    public static final Error CANNOT_DELETE_DEFAULT_ADDRESS =
            new Error("Não é possível remover o endereço padrão. " +
                      "Defina outro endereço como padrão antes de remover este.");
}
