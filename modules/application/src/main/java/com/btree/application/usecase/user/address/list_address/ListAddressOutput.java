package com.btree.application.usecase.user.address.list_address;

import com.btree.domain.user.entity.Address;
import java.util.List;
import java.util.Objects;

/**
 * DTO de saída que representa uma lista de endereços já transformados
 * para um formato adequado à camada de aplicação (output).
 */
public record ListAddressOutput(List<AddressOutputItem> items) {

    /**
     * Método de fábrica responsável por converter uma lista de entidades Address
     * em uma lista de AddressOutputItem.
     *
     * @param addresses lista de entidades vindas do domínio
     * @return um objeto ListAddressOutput contendo os itens convertidos
     */
    public static ListAddressOutput from(final List<Address> addresses) {

        // Se a lista for nula, retorna uma lista vazia (evita NullPointerException)
        final var items = addresses == null
                ? List.<AddressOutputItem>of()

                // Caso contrário, faz o processamento da lista
                : addresses.stream()

                // Remove possíveis elementos nulos da lista
                  .filter(Objects::nonNull)

                // Converte cada Address em AddressOutputItem
                  .map(AddressOutputItem::from)

                // Coleta o resultado em uma nova lista
                  .toList();

        // Retorna o DTO com a lista já processada
        return new ListAddressOutput(items);
    }
}

