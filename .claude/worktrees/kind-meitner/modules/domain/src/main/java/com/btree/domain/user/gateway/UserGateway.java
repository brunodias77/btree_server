package com.btree.domain.user.gateway;


import com.btree.domain.user.entity.User;
import com.btree.domain.user.identifier.UserId;

public interface UserGateway {
    User save(User user);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    void assignRole(UserId userId, String roleName);
    java.util.Optional<User> findByEmail(String email);
    java.util.Optional<User> findById(UserId id);
    java.util.Optional<User> findByUsernameOrEmail(String identifier);
    User update(User user);
}
