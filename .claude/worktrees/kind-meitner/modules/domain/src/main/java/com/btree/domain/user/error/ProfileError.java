package com.btree.domain.user.error;

import com.btree.shared.validation.Error;

public final class ProfileError {
    private ProfileError() {}

    public static final Error CPF_INVALID                 = new Error("CPF inválido");
    public static final Error NAME_TOO_LONG               = new Error("Primeiro nome ou sobrenome excede o tamanho máximo de 100 caracteres");
    public static final Error INVALID_PHONE_NUMBER        = new Error("Número de telefone deve seguir o formato E.164");
    public static final Error CPF_ALREADY_IN_USE          = new Error("CPF já está em uso por outro usuário");
    public static final Error FIRST_NAME_EMPTY            = new Error("'firstName' não pode estar vazio quando fornecido");
    public static final Error PREFERRED_LANGUAGE_INVALID  = new Error("'preferredLanguage' deve ter entre 2 e 10 caracteres");
    public static final Error PREFERRED_CURRENCY_INVALID  = new Error("'preferredCurrency' deve ter exatamente 3 caracteres");
    public static final Error PROFILE_NOT_FOUND           = new Error("Perfil não encontrado para o usuário informado");
}