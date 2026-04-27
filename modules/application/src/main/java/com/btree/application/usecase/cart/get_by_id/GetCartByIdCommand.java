package com.btree.application.usecase.cart.get_by_id;

public record GetCartByIdCommand(
        String userId,
        String sessionId
) {}