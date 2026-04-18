package com.btree.domain.user.error;

import com.btree.shared.validation.Error;

public final class UserNotificationError {
    private UserNotificationError() {}

    public static final Error TITLE_EMPTY = new Error("'title' não pode estar vazio");
    public static final Error MESSAGE_EMPTY = new Error("'message' não pode estar vazia");
    public static final Error NOTIFICATION_TYPE_EMPTY = new Error("'notificationType' não pode estar vazio");
}
