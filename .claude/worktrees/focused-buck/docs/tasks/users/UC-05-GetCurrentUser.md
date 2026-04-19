# Task: UC-05 — GetCurrentUser

## 📋 Resumo
O caso de uso `GetCurrentUser` é responsável por recuperar e retornar os dados consolidados do usuário atualmente autenticado. Isso inclui informações básicas da conta (email, roles, status) e os dados de seu perfil (nome, preferências, etc.). É a rota principal consumida pelos front-ends (Web, Mobile) logo após o login para renderizar o contexto do usuário (ex: Avatar, Nome na Navbar).

## 🎯 Objetivo
Dado o ID do usuário (extraído de forma segura do JWT no contexto da requisição), o sistema deve buscar a entidade `User` correspondente no banco de dados e projetar esses dados em um `Output` seguro para a API, omitindo informações sensíveis como hash de senhas.

## 📦 Contexto Técnico
* **Módulo Principal:** `users`
* **Tipo:** Query (Leitura, sem mutações)
* **Prioridade:** `CRÍTICO` (P0)
* **Endpoint:** `GET /v1/users/me`
* **Tabelas do Banco:** `users.users`, `users.profiles`, `users.roles`, `users.user_roles`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. `domain/src/main/java/com/btree/domain/users/errors/UserError.java` (Criar caso não exista para agrupar erros como `USER_NOT_FOUND`)

### `application`
1. `application/src/main/java/com/btree/application/users/usecases/query/getcurrent/IGetCurrentUserUseCase.java`
2. `application/src/main/java/com/btree/application/users/usecases/query/getcurrent/GetCurrentUserUseCase.java`
3. `application/src/main/java/com/btree/application/users/usecases/query/getcurrent/GetCurrentUserInput.java`
4. `application/src/main/java/com/btree/application/users/usecases/query/getcurrent/GetCurrentUserOutput.java`

### `infrastructure`
1. *(Nenhuma alteração esperada se o `IUserGateway.findById(UserID)` já carrega a Entidade com o Perfil anexado via JPA/Hibernate)*

### `api`
1. `api/src/main/java/com/btree/api/users/UsersController.java` (Novo Controller para agrupar rotas de `/v1/users`)
2. `api/src/main/java/com/btree/api/configurations/modules/UsersModuleConfig.java` (Registrar Bean do `GetCurrentUserUseCase`)

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)
* Este é um Use Case de consulta. Não instanciamos ou mutamos a entidade `User` (Aggregate Root).
* Criaremos (ou atualizaremos) a classe `UserError` para conter `UserError.USER_NOT_FOUND` (usado caso, de forma anômala, o ID contido no JWT já não exista mais na base de dados).

### 2. Contrato de Entrada/Saída (Application)
* **`GetCurrentUserInput` (Record):** `userId` (String).
* **`GetCurrentUserOutput` (Record):** 
  * `id` (String)
  * `email` (String)
  * `roles` (List<String>)
  * `profile` (Nested Record contendo: `firstName`, `lastName`, `displayName`, `avatarUrl`, etc.)
  * `createdAt` (Instant)

### 3. Lógica do Use Case (Application)
1. Instanciar `Notification.create()`.
2. Validar se o `userId` foi fornecido. Se estiver nulo/vazio, retornar erro genérico.
3. Buscar o usuário através do gateway: `userGateway.findById(new UserID(input.userId()))`.
4. Se `Optional` estiver vazio, retornar `Left(UserError.USER_NOT_FOUND)`.
5. Extrair os dados da Entidade `User` e de sua propriedade `Profile` (se existir).
6. Montar e retornar `Right(new GetCurrentUserOutput(...))`.
   * *Atenção:* Certifique-se de mapear as Roles corretamente para a lista de strings na saída.

### 4. Persistência (Infrastructure)
* A persistência não será alterada. O método `findById` do `UserPostgresGateway` mapeará o retorno do Spring Data JPA (`UserJpaEntity`) que já deve possuir os relacionamentos mapeados (`@OneToOne` para Profile, `@ManyToMany` para Roles) configurados para Eager ou Lazy com fetch join.

### 5. Roteamento e Injeção (API)
* Adicionar o `@Bean` no arquivo `UsersModuleConfig.java`.
* Criar a classe `UsersController` com `@RequestMapping("/v1/users")`.
* Criar o endpoint `@GetMapping("/me")`.
* Extrair o ID do usuário do contexto do Spring Security:
  ```java
  Authentication auth = SecurityContextHolder.getContext().getAuthentication();
  String userId = auth.getName();
  ```
* Executar o Use Case repassando esse ID.
* Se erro, retornar `404 Not Found` (ou outro status apropriado).
* Se sucesso, retornar `200 OK` com `ApiResponse.success(output)`.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
| --------------- | -------- | ---------------------- |
| `UserError.USER_NOT_FOUND` | ID extraído do token não corresponde a nenhum usuário ativo no DB | `404 Not Found` |

---

## 🌐 Contrato da API REST

### Request
```http
GET /v1/users/me
Authorization: Bearer eyJhbGciOi...
```

### Response (Sucesso)
```json
{
  "data": {
    "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "email": "bruno@admin.com",
    "roles": ["ADMIN"],
    "profile": {
      "first_name": "Bruno",
      "last_name": "Admin",
      "display_name": "Bruno Admin"
    },
    "created_at": "2026-04-06T10:00:00Z"
  },
  "success": true,
  "timestamp": "2026-04-06T12:00:00Z"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. Criar `UserError`.
2. Criar os contratos na camada de aplicação (`Input`, `Output`, `IUseCase`).
3. Implementar a lógica de leitura e projeção no `GetCurrentUserUseCase`.
4. Criar a configuração do `@Bean` em `UsersModuleConfig`.
5. Criar `UsersController` e adicionar o endpoint `/me`.
6. Testar o endpoint via cURL ou Postman utilizando um token válido gerado previamente.