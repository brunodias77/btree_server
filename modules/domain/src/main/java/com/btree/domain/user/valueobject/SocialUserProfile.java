package com.btree.domain.user.valueobject;

/**
 * Value Object com os dados retornados por um provedor de login social (OAuth2).
 * Usado como entrada nos use cases de autenticação social — não é persistido diretamente.
 */
public record SocialUserProfile(
        String providerUserId,
        String email,
        String firstName,
        String lastName,
        String pictureUrl
) {
    public SocialUserProfile {
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("'providerUserId' não pode ser nulo ou vazio");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("'email' não pode ser nulo ou vazio");
        }
    }
}
