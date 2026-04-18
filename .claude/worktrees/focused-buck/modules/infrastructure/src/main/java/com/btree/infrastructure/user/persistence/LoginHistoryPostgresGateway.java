package com.btree.infrastructure.user.persistence;

import com.btree.domain.user.entity.LoginHistory;
import com.btree.domain.user.gateway.LoginHistoryGateway;
import com.btree.infrastructure.user.entity.LoginHistoryJpaEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class LoginHistoryPostgresGateway implements LoginHistoryGateway {

    private final LoginHistoryJpaRepository loginHistoryJpaRepository;

    public LoginHistoryPostgresGateway(final LoginHistoryJpaRepository loginHistoryJpaRepository) {
        this.loginHistoryJpaRepository = loginHistoryJpaRepository;
    }

    @Override
    public LoginHistory create(final LoginHistory loginHistory) {
        return loginHistoryJpaRepository
                .save(LoginHistoryJpaEntity.from(loginHistory))
                .toAggregate();
    }
}

