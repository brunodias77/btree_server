package com.btree.domain.user.gateway;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.identifier.UserId;

import java.util.Optional;

public interface ProfileGateway {

    Profile create(Profile profile);

    Profile update(Profile profile);

    Optional<Profile> findByUserId(UserId userId);

    boolean existsByCpfAndNotUserId(String cpf, UserId userId);
}
