package com.btree.domain.user.gateway;


import com.btree.domain.user.entity.UserToken;
import com.btree.domain.user.identifier.UserTokenId;

import java.util.Optional;

public interface UserTokenGateway {
    UserToken create(UserToken userToken);
    UserToken update(UserToken userToken);
    Optional<UserToken> findByTokenHash(String tokenHash);
    Optional<UserToken> findById(UserTokenId id);

    /**
     * Remove fisicamente tokens expirados em lote.
     *
     * @param batchSize limite máximo de registros a deletar nesta execução
     * @return número de registros efetivamente deletados
     */
    int deleteExpired(int batchSize);
}
