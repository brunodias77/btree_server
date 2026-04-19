//package com.btree.infrastructure.user.persistence;
//
//import com.btree.domain.user.entity.User;
//import com.btree.infrastructure.IntegrationTest;
//import com.btree.shared.validation.Notification;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@DisplayName("Integration Test: UserPostgresGateway")
//class UserPostgresGatewayTest extends IntegrationTest {
//
//    @Autowired
//    private UserPostgresGateway gateway;
//
//    @Autowired
//    private UserJpaRepository repository;
//
//    @BeforeEach
//    void cleanUp() {
//        repository.deleteAll();
//    }
//
//    @Test
//    @DisplayName("Deve salvar um User (Aggregate) inteiro convertendo perfeitamente para as entidades JPA")
//    void shouldSaveUserAggregateSuccessfully() {
//        // Construindo a Raiz de Agregação pelo método rico do DDD
//        final var notification = Notification.create();
//        final var domainUser = User.create("brunodias", "bruno@gmail.com", "PasswordStrong123!", notification);
//
//        // Dispara a operação no Gateway (Aonde a mágica de conversão acontece)
//        final var savedUser = gateway.save(domainUser);
//
//        // Verificações na resposta traduzida
//        assertThat(savedUser).isNotNull();
//        assertThat(savedUser.getId()).isEqualTo(domainUser.getId());
//        assertThat(savedUser.getUsername()).isEqualTo("brunodias");
//        assertThat(savedUser.getProfile()).isNotNull(); // Prova que as subentidades vieram
//
//        // Verifica no banco de dados "nativo" pulando o gateway (Sanity Check)
//        final var nativeEntity = repository.findById(domainUser.getId().getValue());
//
//        assertThat(nativeEntity).isPresent();
//        assertThat(nativeEntity.get().getEmail()).isEqualTo("bruno@gmail.com");
//    }
//
//    @Test
//    @DisplayName("Deve ser capaz de recompor a Entidade de Domínio perfeitamente a partir do Banco")
//    void shouldFindByIdAndConvertToAggregate() {
//        final var notification = Notification.create();
//        final var domainUser = User.create("brunotest", "test@test.com", "StrongPassword123!", notification);
//        gateway.save(domainUser);
//
//        final var foundUser = gateway.findById(domainUser.getId());
//
//        assertThat(foundUser).isPresent();
//        assertThat(foundUser.get().getUsername()).isEqualTo("brunotest");
//        assertThat(foundUser.get().getProfile()).isNotNull();
//        assertThat(foundUser.get().getNotificationPreference()).isNotNull();
//    }
//}
