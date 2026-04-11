package com.btree.domain.user.gateway;


import com.btree.domain.user.entity.NotificationPreference;

public interface NotificationPreferenceGateway {
    NotificationPreference create(NotificationPreference preference);
}
