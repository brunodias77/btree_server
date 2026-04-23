package com.btree.domain.cart.gateway;

import com.btree.domain.cart.entity.SavedCart;
import com.btree.domain.cart.identifier.SavedCartId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedCartGateway {

    SavedCart save(SavedCart savedCart);

    SavedCart update(SavedCart savedCart);

    Optional<SavedCart> findById(SavedCartId id);

    List<SavedCart> findByUserId(UUID userId);

    void deleteById(SavedCartId id);
}
