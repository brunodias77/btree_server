package com.btree.domain.user.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do Record SocialUserProfile")
public class SocialUserProfileTest {

    @Test
    @DisplayName("Deve instanciar corretamente validando o pacto do construtor")
    void deveInstanciarCorretamente() {
        final var providerUserId = "123456";
        final var email = "oauth@mail.com";
        final var firstName = "Bruno";
        final var lastName = "Dias";
        final var pictureUrl = "http://foto.com/1";

        final var profile = new SocialUserProfile(providerUserId, email, firstName, lastName, pictureUrl);

        assertNotNull(profile);
        assertEquals(providerUserId, profile.providerUserId());
        assertEquals(email, profile.email());
        assertEquals(firstName, profile.firstName());
        assertEquals(lastName, profile.lastName());
        assertEquals(pictureUrl, profile.pictureUrl());
    }

    @Test
    @DisplayName("Deve lançar exceção se providerUserId for null ou vazia")
    void deveRejeitarProviderIdInvalido() {
        assertThrows(IllegalArgumentException.class, 
                () -> new SocialUserProfile(null, "email@mail.com", "F", "L", "pic"));
        
        assertThrows(IllegalArgumentException.class, 
                () -> new SocialUserProfile("   ", "email@mail.com", "F", "L", "pic"));
    }

    @Test
    @DisplayName("Deve lançar exceção se email for null ou vazio")
    void deveRejeitarEmailInvalido() {
        assertThrows(IllegalArgumentException.class, 
                () -> new SocialUserProfile("123", null, "F", "L", "pic"));
        
        assertThrows(IllegalArgumentException.class, 
                () -> new SocialUserProfile("123", "   ", "F", "L", "pic"));
    }
}
