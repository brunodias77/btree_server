# Domain Events e Integration Events

## Conceitos

**Domain Event** — algo que aconteceu dentro de um bounded context, na mesma transação. Publicado pelo aggregate, persistido no outbox (`shared.domain_events`), processado pelo job `ProcessDomainEventsUseCase`.

**Integration Event** — evento cross-context publicado *após* o domain event ser processado. Representa a notificação para outros contextos (ex: `orders` notificando `payments`). Também passa pelo outbox.

---

## Domain Event

### Localização

```
domain/user/event/UserCreatedEvent.java
```

### Implementação

```java
package com.btree.domain.user.event;

import com.btree.shared.domain.DomainEvent;

public class UserCreatedEvent extends DomainEvent {

    private final String userId;
    private final String email;

    public UserCreatedEvent(final String userId, final String email) {
        super();
        this.userId = userId;
        this.email = email;
    }

    @Override
    public String getAggregateId() {
        return userId;
    }

    @Override
    public String getEventType() {
        return "user.created";
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}
```

### Como o Aggregate registra o evento

```java
// domain/user/User.java
public class User extends AggregateRoot<UserId> {

    public static User create(final String name, final String email, final String passwordHash) {
        final var user = new User(UserId.unique(), name, email, passwordHash);
        user.registerEvent(new UserCreatedEvent(user.getId().getValue(), email));
        return user;
    }
}
```

### Como o Use Case publica após o commit

```java
// application/user/create/RegisterUserUseCase.java
public class RegisterUserUseCase extends UseCase<RegisterUserCommand, RegisterUserOutput> {

    private final UserGateway userGateway;
    private final DomainEventPublisher publisher;

    public RegisterUserUseCase(final UserGateway userGateway, final DomainEventPublisher publisher) {
        this.userGateway = userGateway;
        this.publisher = publisher;
    }

    @Override
    public RegisterUserOutput execute(final RegisterUserCommand command) {
        final var user = User.create(command.name(), command.email(), command.passwordHash());
        userGateway.save(user);
        publisher.publishAll(user.getDomainEvents());
        user.clearDomainEvents();
        return RegisterUserOutput.from(user);
    }
}
```

---

## Integration Event

### Localização

```
domain/user/event/UserCreatedIntegrationEvent.java
```

### Implementação

```java
package com.btree.domain.user.event;

import com.btree.shared.event.IntegrationEvent;

public class UserCreatedIntegrationEvent extends IntegrationEvent {

    private final String userId;
    private final String email;

    public UserCreatedIntegrationEvent(final String userId, final String email) {
        super("users");
        this.userId = userId;
        this.email = email;
    }

    @Override
    public String getEventType() {
        return "user.created";
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}
```

### Quem publica

O handler do domain event, na camada `application`, converte o domain event em integration event e o publica:

```java
// application/user/event/OnUserCreatedHandler.java
public class OnUserCreatedHandler {

    private final IntegrationEventPublisher integrationPublisher;

    public OnUserCreatedHandler(final IntegrationEventPublisher integrationPublisher) {
        this.integrationPublisher = integrationPublisher;
    }

    public void handle(final UserCreatedEvent event) {
        integrationPublisher.publish(
            new UserCreatedIntegrationEvent(event.getAggregateId(), event.getEmail())
        );
    }
}
```

---

## Fluxo Completo

```
User.create()
  └─ registerEvent(UserCreatedEvent)

RegisterUserUseCase.execute()
  ├─ userGateway.save(user)          ← persiste user + outbox na mesma transação
  └─ publisher.publishAll(events)    ← grava em shared.domain_events

[job] ProcessDomainEventsUseCase
  └─ OnUserCreatedHandler.handle()
       └─ integrationPublisher.publish(UserCreatedIntegrationEvent)
            └─ outros contextos consomem (ex: envio de e-mail de boas-vindas)
```

---

## Convenções

| Tipo | Sufixo | Pacote | Quem cria |
|---|---|---|---|
| Domain Event | `*Event` | `domain/<contexto>/event/` | O próprio Aggregate |
| Integration Event | `*IntegrationEvent` | `domain/<contexto>/event/` | Handler do domain event |
| Handler do domain event | `On*Handler` | `application/<contexto>/event/` | Use Case / handler |
