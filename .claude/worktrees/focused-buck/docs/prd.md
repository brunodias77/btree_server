# PRD — E-commerce Monolítico Modular

## 1. Resumo Executivo

Plataforma de e-commerce B2C construída como monolito modular em Java/Spring Boot, voltada para o mercado brasileiro. O sistema cobre o ciclo completo de compra: cadastro de usuários, catálogo de produtos, carrinho, checkout, pagamentos (cartão, PIX, boleto), cupons de desconto, rastreamento de pedidos e emissão de NF-e.

A arquitetura modular (Clean Architecture com 5 módulos Maven) permite evolução independente de cada bounded context, com possibilidade futura de extração para microsserviços sem reescrita da lógica de negócio.

## 2. Objetivos

- Entregar um fluxo de compra completo e funcional (do cadastro à entrega)
- Suportar múltiplos métodos de pagamento do mercado brasileiro (PIX, boleto, cartão com parcelamento)
- Garantir integridade de estoque com reserva pessimista e expiração automática
- Permitir operação administrativa (gestão de pedidos, cupons, chargebacks, auditoria)
- Manter a lógica de negócio 100% isolada de framework, testável sem Spring

## 3. Público-Alvo

| Persona | Descrição |
|---|---|
| **Consumidor** | Usuário final que navega, compra e acompanha pedidos |
| **Administrador** | Operador que gerencia catálogo, pedidos, cupons, reembolsos e chargebacks |

## 4. Stack Tecnológica

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21+ |
| Framework | Spring Boot (Web MVC, Data JPA, Security, Validation, Actuator) |
| Banco de dados | PostgreSQL (com particionamento, UUIDs v7, extensões citext e pg_trgm) |
| Migrações | Flyway |
| Autenticação | JWT (JJWT 0.12.6) + TOTP para 2FA |
| Documentação API | Springdoc OpenAPI 2.8.6 (Swagger UI) |
| Utilitários | Lombok, Vavr 0.10.4 |
| Testes | JUnit 5, Mockito, Testcontainers (PostgreSQL) |

## 5. Arquitetura

Monolito modular com Clean Architecture dividido em 5 módulos Maven:

| Módulo | Spring? | Responsabilidade |
|---|---|---|
| `shared` | Não | Value Objects, enums, abstrações (UseCase, Entity, AggregateRoot), validação, paginação, contratos |
| `domain` | Não | Entidades, Aggregates, Gateways (interfaces), Domain Events, Validators |
| `application` | Não | 171 Use Cases, Commands (input), Outputs (saída), Event Handlers, Jobs |
| `infrastructure` | Sim | JPA Entities/Repositories/Gateways, Security (JWT/TOTP), Flyway, configs Spring |
| `api` | Sim | REST Controllers, DTOs HTTP, Exception Handlers, Swagger, `@SpringBootApplication` |

O banco de dados usa 7 schemas PostgreSQL: `shared`, `users`, `catalog`, `cart`, `orders`, `payments`, `coupons`.

## 6. Módulos Funcionais

### 6.1. Usuários e Autenticação (44 use cases)

**Registro e login**: Cadastro com email/senha, autenticação com geração de JWT (access + refresh token), renovação de sessão, logout individual e global. Login social (OAuth com Google/Facebook) como funcionalidade secundária.

**Segurança**: Verificação de email via token, reset de senha, 2FA com TOTP, bloqueio de conta por tentativas falhas, controle de sessões ativas com device fingerprint.

**Perfil e endereços**: Edição de dados pessoais (nome, CPF, preferências de idioma/moeda), múltiplos endereços de entrega com marcação de padrão, soft delete.

**Autorização**: Sistema de roles e authorities. Admin pode atribuir/revogar roles, conceder permissões granulares, desabilitar/bloquear contas.

**Notificações**: Sistema de notificações internas com preferências por canal (email, push, SMS). Marcação de leitura individual e em lote.

**Sessões e histórico**: Visualização de sessões ativas, revogação individual, histórico de login com IP/device/geolocalização (tabela particionada por trimestre).

**Jobs**: Limpeza automática de tokens e sessões expirados.

### 6.2. Catálogo (42 use cases)

**Categorias**: Hierarquia com até 5 níveis de profundidade, campo `path` para breadcrumb, ordenação customizada, soft delete. Busca por trigram (pg_trgm).

**Marcas**: CRUD com slug único, logo, website, soft delete.

**Produtos**: Ciclo de vida completo via status (`DRAFT` → `ACTIVE` → `PAUSED` → `ARCHIVED`). Campos para preço, preço comparativo, preço de custo, dimensões, peso, atributos JSON, tags (array). Busca full-text com trigram, filtros por categoria/marca/preço/atributos/tags. Suporte a produto digital e produto que não requer frete.

**Imagens de produto**: Upload múltiplo com variantes (thumbnail, medium, large), reordenação, marcação de imagem primária.

**Estoque**: Controle com movimentações rastreadas (entrada, saída, reserva, liberação, ajuste, devolução). Reserva pessimista com `SELECT FOR UPDATE`, expiração automática via job. Alerta de estoque baixo baseado em `low_stock_threshold`. Tabela de movimentações particionada por trimestre.

**Reviews**: Avaliação de 1-5 estrelas com título e comentário. Requer compra verificada (opcional). Moderação por admin (aprovação), resposta do vendedor. Soft delete. Sumário com média e contagem.

**Favoritos (Wishlist)**: Adicionar/remover produtos com snapshot do produto no momento da adição.

### 6.3. Carrinho (13 use cases)

**Carrinho principal**: Suporte a usuário autenticado e visitante (via `session_id`). Adicionar item com snapshot do produto e preço no momento, alterar quantidade, remover (soft delete via `removed_at`). Visualização com preços atualizados em tempo real (campo `current_price` vs `unit_price`).

**Merge de carrinho**: Ao fazer login, carrinho do visitante é unido ao do usuário. Conflitos resolvidos somando quantidades respeitando estoque disponível.

**Carrinho salvo**: Salvar snapshot do carrinho atual para restauração futura. Listar e deletar carrinhos salvos.

**Cupom no carrinho**: Aplicar/remover cupom com cálculo de desconto em tempo real.

**Detecção de mudança de preço**: Event handler que atualiza `current_price` quando o produto sofre alteração.

**Expiração**: Job que marca carrinhos inativos como `EXPIRED`/`ABANDONED`.

### 6.4. Pedidos e Checkout (23 use cases)

**Checkout**: Fluxo em etapas — `InitiateCheckout` (valida carrinho, reserva estoque, calcula totais) → `SelectShippingMethod` → `PlaceOrder` (cria pedido, marca carrinho como CONVERTED). Cálculo de totais: subtotal − desconto + frete + imposto = total, com constraint de integridade no banco (`ABS(total - cálculo) < 0.01`).

**Máquina de estados do pedido**: `PENDING` → `CONFIRMED` → `PROCESSING` → `SHIPPED` → `DELIVERED`. Cancelamento possível em qualquer estado pré-envio, com motivo obrigatório (enum `cancellation_reason`). Histórico completo de transições em `orders.status_history`.

**Rastreamento**: Registro de eventos de rastreio com código, descrição, localização, cidade, estado e timestamp.

**NF-e**: Emissão de nota fiscal com número, chave, série, URLs de PDF e XML.

**Reembolso (fluxo order-side)**: Solicitação com `payment_id = NULL` inicial → aprovação em `ProcessOrderRefund` (preenche `payment_id`, aciona gateway) → atualização de estoque se devolução física.

**Número do pedido**: Formato `ORD-YYYYMMDD-XXXXXXXXX` (25 chars), gerado por função PostgreSQL.

### 6.5. Pagamentos (25 use cases)

**Métodos salvos**: Tokenização de cartão via gateway externo. Armazena últimos 4 dígitos, bandeira, validade. Marcação de método padrão. Soft delete.

**Processamento**: Suporte a cartão de crédito/débito (com parcelamento até 24x), PIX (QR code com expiração), boleto (com código de barras e expiração), wallet e transferência bancária. Fluxo: `CreatePayment` → `AuthorizePayment` → `CapturePayment`. Idempotência via `idempotency_key`. Proteção contra dupla captura via unique index parcial no banco.

**Void**: Estorno pré-captura para cancelamentos antes da captura.

**Reembolso (fluxo payment-side)**: Processamento no gateway com idempotência, registro em `payments.refunds` vinculado a `orders.refunds`.

**Chargebacks**: Registro de chargeback recebido, envio de evidência de contestação, resolução (WON/LOST), com prazo de evidência rastreado.

**Webhooks**: Padrão inbox — `ReceiveWebhook` grava payload cru com idempotência via `gateway_event_id` (unique index previne duplicatas). Job `ProcessPendingWebhooks` processa fila e despacha eventos. Tabela particionada por trimestre.

**Jobs**: Cancelamento automático de PIX/boleto expirados.

### 6.6. Cupons de Desconto (17 use cases)

**Tipos**: Percentual, valor fixo, frete grátis, compre X leve Y.

**Escopo**: Global (ALL), por categoria, por produto, por usuário.

**Ciclo de vida**: `DRAFT` → `ACTIVE` → `PAUSED` → `EXPIRED`/`DEPLETED`. Expiração automática via job quando `valid_until < NOW()` ou `current_uses = max_uses`.

**Regras de elegibilidade**: Valor mínimo de compra, quantidade mínima de itens, limite de usos total e por usuário, período de validade, stackability.

**Fluxo no checkout**: Validação → reserva no carrinho → confirmação de uso ao criar pedido → reversão em caso de cancelamento. Reservas expiradas são limpas por job.

### 6.7. Infraestrutura e Operações (7 use cases)

**Domain Events (Outbox)**: Eventos persistidos em `shared.domain_events` com `processed_at`, `error_message`, `retry_count`. Job de polling processa pendentes. Idempotência garantida por `shared.processed_events`. Retry de eventos falhos com limite. Archival de eventos processados.

**Auditoria**: Registro de ações com old/new values em JSON, rastreando usuário, IP e user agent. Consulta por entidade ou por usuário. Tabela particionada por trimestre.

## 7. Requisitos Não-Funcionais

**Integridade de dados**: Constraints CHECK no banco para preços, quantidades, totais. Optimistic locking via coluna `version`. Unique indexes parciais com `WHERE deleted_at IS NULL` para soft delete.

**Segurança**: Senhas armazenadas com BCrypt. Tokens com hash (nunca em texto plano). JWT com access/refresh token. 2FA via TOTP. Bloqueio de conta após tentativas falhas com expiração. Proteção CSRF via stateless JWT.

**Observabilidade**: Spring Actuator para métricas e health checks. Audit log completo. Login history com device fingerprint.

**Performance**: Indexes estratégicos (trigram para busca, GIN para JSONB/arrays, parciais para status). Particionamento trimestral em tabelas de alto volume. UUIDs v7 (time-ordered) para performance em B-tree.

**Testes**: Unitários sem framework (JUnit 5 + Mockito) para domain e application. Integração com Testcontainers PostgreSQL para infrastructure. E2E com app completa para api.

## 8. Modelo de Dados

### 8.1. Schemas e Tabelas

| Schema | Tabelas | Particionadas |
|---|---|---|
| `shared` | `domain_events`, `processed_events`, `audit_logs` | `domain_events`, `audit_logs` |
| `users` | `users`, `roles`, `user_roles`, `user_authorities`, `user_social_logins`, `user_tokens`, `profiles`, `addresses`, `login_history`, `sessions`, `notifications`, `notification_preferences` | `login_history` |
| `catalog` | `categories`, `brands`, `products`, `product_images`, `stock_movements`, `stock_reservations`, `product_reviews`, `user_favorites` | `stock_movements` |
| `cart` | `carts`, `items`, `activity_log`, `saved_carts` | — |
| `orders` | `orders`, `items`, `status_history`, `tracking_events`, `invoices`, `refunds` | — |
| `payments` | `user_methods`, `payments`, `transactions`, `refunds`, `chargebacks`, `webhooks` | `webhooks` |
| `coupons` | `coupons`, `eligible_categories`, `eligible_products`, `eligible_users`, `usages`, `reservations` | — |

### 8.2. Enums Compartilhados (schema `shared`)

`product_status`, `stock_movement_type`, `cart_status`, `order_status`, `shipping_method`, `payment_method_type`, `payment_status`, `transaction_type`, `coupon_type`, `coupon_scope`, `coupon_status`, `card_brand`, `cancellation_reason`, `token_type`, `refund_status`, `chargeback_status`

## 9. Plano de Entrega

| Sprint | Escopo | Prioridade | Use Cases |
|---|---|---|---|
| **Sprint 1** | Users P0 + Catalog P0 | MVP | #1-5, 15-21, 45-48, 52-55, 57-64, 70-73 |
| **Sprint 2** | Cart P0 + Orders P0 + Payments P0 | MVP | #87-90, 100-107, 128-132, 146-147 |
| **Sprint 3** | Users P1 + Catalog P1 | Core | #6-8, 22, 25-29, 49-50, 56, 65-67, 74-75 |
| **Sprint 4** | Orders P1 + Payments P1 | Core | #108-113, 116-122, 123-127, 133-134, 139-140 |
| **Sprint 5** | Coupons P1 + Shared P1 | Core | #148-161, 165-166 |
| **Sprint 6** | Cart P2 + Reviews + Notificações | UX | #93-99, 77-86, 35-40 |
| **Sprint 7** | Audit + Jobs | Ops | #167, 169-171, 13-14, 76, 95, 142, 163 |
| **Sprint 8** | Social login + 2FA + Chargebacks + P3 | Avançado | #9-11, 141, 143-145, itens P3 |

### Totais por prioridade

| Prioridade | Quantidade |
|---|---|
| 🔴 P0 — MVP | 51 |
| 🟠 P1 — Essencial | 58 |
| 🟡 P2 — Importante | 50 |
| 🟢 P3 — Diferencial | 12 |
| **Total** | **171** |