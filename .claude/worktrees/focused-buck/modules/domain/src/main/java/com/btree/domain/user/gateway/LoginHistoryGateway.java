package com.btree.domain.user.gateway;

import com.btree.domain.user.entity.LoginHistory;

public interface LoginHistoryGateway {
    LoginHistory create(LoginHistory loginHistory);
}
