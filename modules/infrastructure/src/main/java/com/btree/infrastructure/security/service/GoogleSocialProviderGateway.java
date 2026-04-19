package com.btree.infrastructure.security.service;

import com.btree.domain.user.gateway.SocialProviderGateway;
import com.btree.domain.user.valueobject.SocialUserProfile;
import com.btree.infrastructure.config.GoogleOAuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Component
public class GoogleSocialProviderGateway implements SocialProviderGateway {

    private static final Logger log = LoggerFactory.getLogger(GoogleSocialProviderGateway.class);
    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final GoogleOAuthConfig googleOAuthConfig;
    private final RestTemplate restTemplate;

    public GoogleSocialProviderGateway(final GoogleOAuthConfig googleOAuthConfig) {
        this.googleOAuthConfig = googleOAuthConfig;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public Optional<SocialUserProfile> validateTokenAndGetProfile(final String provider, final String token) {
        if (!"google".equalsIgnoreCase(provider)) {
            return Optional.empty();
        }

        try {
            final ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    TOKENINFO_URL + token,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }

            final Map<String, Object> claims = response.getBody();

            final String aud = (String) claims.get("aud");
            if (!googleOAuthConfig.getClientId().equals(aud)) {
                log.warn("Google token audience mismatch: expected {}, got {}", googleOAuthConfig.getClientId(), aud);
                return Optional.empty();
            }

            final String sub   = (String) claims.get("sub");
            final String email = (String) claims.get("email");

            if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new SocialUserProfile(
                    sub,
                    email,
                    (String) claims.getOrDefault("given_name", ""),
                    (String) claims.getOrDefault("family_name", ""),
                    (String) claims.get("picture")
            ));

        } catch (final HttpClientErrorException e) {
            log.debug("Google token validation failed (status {}): {}", e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (final Exception e) {
            log.error("Unexpected error validating Google token", e);
            return Optional.empty();
        }
    }
}
