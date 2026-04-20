package com.btree.application.usecase;

import com.btree.shared.contract.TransactionManager;
import com.btree.shared.domain.Identifier;
import com.btree.shared.validation.Notification;
import org.junit.jupiter.api.Tag;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Tag("unitTest")
public abstract class UseCaseTest {

    protected Set<String> asString(final Set<? extends Identifier> ids) {
        return ids.stream()
                .map(Identifier::toString)
                .collect(Collectors.toSet());
    }

    protected List<String> asString(final List<? extends Identifier> ids) {
        return ids.stream()
                .map(Identifier::toString)
                .toList();
    }

    protected Set<String> asStringSet(final Collection<? extends Identifier> ids) {
        return ids.stream()
                .map(Identifier::toString)
                .collect(Collectors.toSet());
    }

    protected List<String> asStringList(final Collection<? extends Identifier> ids) {
        return ids.stream()
                .map(Identifier::toString)
                .toList();
    }

    protected List<String> errors(final Notification notification) {
        return notification.getErrors().stream()
                .map(com.btree.shared.validation.Error::message)
                .toList();
    }

    protected String firstError(final Notification notification) {
        return notification.firstError() != null
                ? notification.firstError().message()
                : null;
    }

    public static final class ImmediateTransactionManager implements TransactionManager {
        @Override
        public <T> T execute(final Supplier<T> action) {
            return action.get();
        }

        @Override
        public void executeVoid(final Runnable action) {
            action.run();
        }
    }

}
