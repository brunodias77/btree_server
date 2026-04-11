package com.btree.domain.user.gateway;



import com.btree.domain.user.valueobject.SocialUserProfile;

import java.util.Optional;

public interface SocialProviderGateway {
    Optional<SocialUserProfile> validateTokenAndGetProfile(String provider, String token);
}
