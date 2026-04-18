package com.btree.domain.user.gateway;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;

import java.util.List;
import java.util.Optional;

public interface AddressGateway {

    /** Persiste um novo endereço. */
    Address save(Address address);

    /** Atualiza um endereço existente (ex: marcar/desmarcar como padrão). */
    Address update(Address address);

    /** Busca endereço pelo ID, incluindo soft-deletados. */
    Optional<Address> findById(AddressId id);

    /** Lista endereços ativos do usuário (excluindo soft-deletados). */
    List<Address> findByUserId(UserId userId);

    /**
     * Conta endereços ativos do usuário.
     * Usado para determinar se o novo endereço deve ser automaticamente padrão.
     */
    long countActiveByUserId(UserId userId);

    /**
     * Remove a marcação de padrão de todos os endereços de entrega do usuário.
     * Chamado antes de marcar um novo endereço como padrão.
     */
    void clearDefaultByUserId(UserId userId);

    /**
     * Conta endereços ativos do usuário excluindo o endereço informado.
     *
     * <p>Usado na regra de negócio de deleção do endereço padrão:
     * se o resultado for 0, o endereço é o único ativo e pode ser deletado
     * mesmo sendo o padrão. Se for > 0, a deleção do padrão é bloqueada.
     *
     * @param userId    ID do usuário proprietário
     * @param excludeId ID do endereço a excluir da contagem
     * @return número de endereços ativos excluindo {@code excludeId}
     */
    long countActiveByUserIdExcluding(UserId userId, AddressId excludeId);
}
