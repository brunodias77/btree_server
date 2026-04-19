//package com.btree.infrastructure.user.persistence;
//
//import com.btree.infrastructure.IntegrationTest;
//import com.btree.infrastructure.user.entity.UserJpaEntity;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//
//import java.time.Instant;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@DisplayName("Integration Test: UserJpaRepository")
//class UserJpaRepositoryTest extends IntegrationTest {
//
//    @Autowired
//    private UserJpaRepository repository;
//
//    @BeforeEach
//    void cleanUp() {
//        repository.deleteAll();
//    }
//
//    private UserJpaEntity buildBareBonesUser(String username, String email) {
//        var entity = new UserJpaEntity();
//        entity.setId(UUID.randomUUID());
//        entity.setUsername(username);
//        entity.setEmail(email);
//        entity.setEmailVerified(false);
//        entity.setPasswordHash("hash123");
//        entity.setPhoneNumberVerified(false);
//        entity.setTwoFactorEnabled(false);
//        entity.setAccountLocked(false);
//        entity.setAccessFailedCount(0);
//        entity.setEnabled(true);
//        entity.setCreatedAt(Instant.now());
//        entity.setUpdatedAt(Instant.now());
//        entity.setVersion(0);
//        return entity;
//    }
//
//    @Test
//    @DisplayName("Deve verificar se email existe ignorando Case")
//    void shouldExistsByEmailIgnoreCase() {
//        final var email = "bruno.dias@b-tree.com";
//        repository.save(buildBareBonesUser("brunod77", email));
//
//        // Letras bagunçadas
//        final var exists = repository.existsByEmailIgnoreCase("BRUno.Dias@B-Tree.com");
//
//        assertThat(exists).isTrue();
//    }
//
//    @Test
//    @DisplayName("Deve verificar se username existe ignorando Case")
//    void shouldExistsByUsernameIgnoreCase() {
//        final var username = "brunod77";
//        repository.save(buildBareBonesUser(username, "bruno.dias@b-tree.com"));
//
//        // Letras bagunçadas
//        final var exists = repository.existsByUsernameIgnoreCase("BrunoD77");
//
//        assertThat(exists).isTrue();
//    }
//
//    @Test
//    @DisplayName("Deve buscar usuário através da query HQL or identifier (via Username)")
//    void shouldFindByIdentifierWhenProvidingUsername() {
//        repository.save(buildBareBonesUser("brunod77", "bruno.dias@b-tree.com"));
//
//        var user = repository.findByUsernameOrEmail("brunod77");
//
//        assertThat(user).isPresent();
//        assertThat(user.get().getUsername()).isEqualTo("brunod77");
//    }
//
//    @Test
//    @DisplayName("Deve buscar usuário através da query HQL or identifier (via Email)")
//    void shouldFindByIdentifierWhenProvidingEmail() {
//        repository.save(buildBareBonesUser("brunod77", "bruno.dias@b-tree.com"));
//
//        var user = repository.findByUsernameOrEmail("bruno.dias@b-tree.com");
//
//        assertThat(user).isPresent();
//        assertThat(user.get().getEmail()).isEqualTo("bruno.dias@b-tree.com");
//    }
//}
