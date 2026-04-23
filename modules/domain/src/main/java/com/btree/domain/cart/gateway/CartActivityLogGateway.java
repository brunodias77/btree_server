package com.btree.domain.cart.gateway;


import com.btree.domain.cart.entity.CartActivityLog;
import com.btree.domain.cart.identifier.CartId;

import java.util.List;

public interface CartActivityLogGateway {

    CartActivityLog save(CartActivityLog log);

    List<CartActivityLog> findByCartId(CartId cartId);
}
