package com.btree.application.usecase.user.address.delete_address;

/**
 * Comando de entrada para UC-19 — DeleteAddress.
 *
 * <p>Não há Output — o use case implementa {@code UnitUseCase}
 * e retorna {@code Either<Notification, Void>}.
 * O controller responde com {@code 204 No Content}.
 *
 * @param userId    ID do usuário autenticado (extraído do JWT)
 * @param addressId ID do endereço a remover
 */
public record DeleteAddressCommand(String userId, String addressId) {}
