package com.btree.domain.user.gateway;



import com.btree.domain.user.entity.UserSocialLogin;
import com.btree.domain.user.identifier.UserId;

import java.util.List;
import java.util.Optional;

public interface UserSocialLoginGateway {
    UserSocialLogin create(UserSocialLogin userSocialLogin);
    Optional<UserSocialLogin> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<UserSocialLogin> findByUserId(UserId userId);
    void deleteByProviderAndProviderUserId(String provider, String providerUserId);
}
