package com.btree.application.usecase.user.address.set_default_address;

/**
 * Comando de entrada para UC-20 — SetDefaultAddress.
 *
 * @param userId    ID do usuário autenticado (extraído do JWT)
 * @param addressId ID do endereço a marcar como padrão
 */
public record SetDefaultAddressCommand(String userId, String addressId) {}