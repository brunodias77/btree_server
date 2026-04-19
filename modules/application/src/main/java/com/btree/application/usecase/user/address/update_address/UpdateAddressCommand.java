package com.btree.application.usecase.user.address.update_address;

/**
 * Comando de entrada para UC-18 — UpdateAddress.
 *
 * @param userId          ID do usuário autenticado (extraído do JWT)
 * @param addressId       ID do endereço a ser atualizado
 * @param label           rótulo do endereço (ex: "Casa", "Trabalho")
 * @param recipientName   nome do destinatário na entrega
 * @param street          logradouro (obrigatório)
 * @param number          número
 * @param complement      complemento (apto, bloco, etc.)
 * @param neighborhood    bairro
 * @param city            cidade (obrigatório)
 * @param state           UF com 2 letras maiúsculas (obrigatório)
 * @param postalCode      CEP no formato XXXXX-XXX ou XXXXXXXX (obrigatório)
 * @param country         país ISO-2 (padrão: "BR")
 * @param isBillingAddress se é endereço de cobrança
 */
public record UpdateAddressCommand(
        String userId,
        String addressId,
        String label,
        String recipientName,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isBillingAddress
) {}
