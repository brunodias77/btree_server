package com.btree.infrastructure.persistence;

import com.btree.shared.contract.TransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class SpringTransactionManager implements TransactionManager {

    private final TransactionTemplate transactionTemplate;

    public SpringTransactionManager(final TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T execute(final Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    @Override
    public void executeVoid(final Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
