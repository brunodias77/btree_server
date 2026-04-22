# Use Cases — Ecommerce Monolith

Lista completa de use cases derivados do schema `db.sql`, organizados por módulo e prioridade de implementação.

**Legenda de prioridade:**
- 🔴 **P0** — MVP / Blocker (sem isso não vende)
- 🟠 **P1** — Essencial para operação (1ª semana pós-launch)
- 🟡 **P2** — Importante para experiência (1º mês)
- 🟢 **P3** — Diferencial competitivo (backlog)

**Legenda de tipo:**
- `[CMD]` — Command (escrita/mutação)
- `[QRY]` — Query (leitura)
- `[EVT]` — Event handler / reação assíncrona
- `[JOB]` — Job/cron agendado

---

## Fase 1 — Fundação (Users + Auth)

> Sem autenticação, nada funciona. Implementar primeiro.

### 1.1 Registro e Identidade

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 1 | 🔴 P0 | `[CMD]` | ✅ **RegisterUser** — Criar conta com email/senha | `users.users`, `users.profiles`, `users.notification_preferences` | 
| 2 | 🔴 P0 | `[CMD]` | ✅ **AuthenticateUser** — Login com email/senha | `users.users`, `users.sessions`, `users.login_history` |
| 3 | 🔴 P0 | `[CMD]` | ✅ **RefreshSession** — Renovar access token via refresh token | `users.sessions` |
| 4 | 🔴 P0 | `[CMD]` | ✅ **LogoutUser** — Revogar sessão atual | `users.sessions` |
| 5 | 🔴 P0 | `[QRY]` | ✅ **GetCurrentUser** — Retornar dados do usuário autenticado | `users.users`, `users.profiles` | 
| 6 | 🟠 P1 | `[CMD]` | ✅ **VerifyEmail** — Confirmar email via token | `users.user_tokens`, `users.users` |
| 7 | 🟠 P1 | `[CMD]` | ✅ **RequestPasswordReset** — Gerar token de reset | `users.user_tokens` |
| 8 | 🟠 P1 | `[CMD]` | ✅ **ResetPassword** — Alterar senha via token | `users.user_tokens`, `users.users` |
| 9 | 🟡 P2 | `[CMD]` | ✅ **LoginWithSocialProvider** — OAuth (Google, Facebook) | `users.user_social_logins`, `users.users`, `users.sessions` |
| 10 | 🟡 P2 | `[CMD]` | ✅ **EnableTwoFactor** — Ativar 2FA (TOTP) | `users.users`, `users.user_tokens` |
| 11 | 🟡 P2 | `[CMD]` | ✅ **VerifyTwoFactor** — Validar código 2FA no login | `users.user_tokens` |
| 12 | 🟡 P2 | `[CMD]` | **LogoutAllSessions** — Revogar todas as sessões | `users.sessions` |
| 13 | 🟢 P3 | `[JOB]` | **CleanupExpiredTokens** — Remover tokens expirados | `users.user_tokens` |
| 14 | 🟢 P3 | `[JOB]` | **CleanupExpiredSessions** — Remover sessões expiradas | `users.sessions` |

### 1.2 Perfil e Endereços

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 15 | 🔴 P0 | `[CMD]` | ✅ **UpdateProfile** — Editar nome, CPF, preferências | `users.profiles` |
| 16 | 🔴 P0 | `[QRY]` | ✅ **GetProfile** — Consultar perfil completo | `users.profiles` |
| 17 | 🔴 P0 | `[CMD]` | ✅ **AddAddress** — Cadastrar endereço de entrega | `users.addresses` |
| 18 | 🔴 P0 | `[CMD]` | ✅ **UpdateAddress** — Editar endereço existente | `users.addresses` |
| 19 | 🔴 P0 | `[CMD]` | ✅ **DeleteAddress** — Soft delete de endereço | `users.addresses` |
| 20 | 🔴 P0 | `[CMD]` | ✅ **SetDefaultAddress** — Marcar como padrão | `users.addresses` |
| 21 | 🔴 P0 | `[QRY]` | ✅ **ListAddresses** — Listar endereços do usuário | `users.addresses` |
| 22 | 🟠 P1 | `[CMD]` | **ChangePassword** — Alterar senha estando autenticado | `users.users` |
| 23 | 🟡 P2 | `[CMD]` | **ChangeEmail** — Alterar email (com reverificação) | `users.users`, `users.user_tokens` |
| 24 | 🟡 P2 | `[CMD]` | **UpdatePhoneNumber** — Atualizar/verificar telefone | `users.users`, `users.user_tokens` |

### 1.3 Autorização (Admin)

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 25 | 🟠 P1 | `[CMD]` | **AssignRole** — Atribuir role a usuário | `users.user_roles`, `users.roles` |
| 26 | 🟠 P1 | `[CMD]` | **RevokeRole** — Remover role de usuário | `users.user_roles` |
| 27 | 🟠 P1 | `[CMD]` | **GrantAuthority** — Conceder permissão específica | `users.user_authorities` |
| 28 | 🟠 P1 | `[CMD]` | **RevokeAuthority** — Remover permissão específica | `users.user_authorities` |
| 29 | 🟠 P1 | `[QRY]` | **ListRoles** — Listar roles disponíveis | `users.roles` |
| 30 | 🟡 P2 | `[CMD]` | **CreateRole** — Criar nova role | `users.roles` |
| 31 | 🟡 P2 | `[CMD]` | **DeleteRole** — Remover role | `users.roles` |
| 32 | 🟡 P2 | `[CMD]` | **DisableAccount** — Desabilitar conta (enabled = false) | `users.users` |
| 33 | 🟢 P3 | `[CMD]` | **LockAccount** — Bloquear conta por fraude/abuso | `users.users` |
| 34 | 🟢 P3 | `[CMD]` | **UnlockAccount** — Desbloquear conta | `users.users` |

### 1.4 Notificações e Sessões

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 35 | 🟡 P2 | `[CMD]` | **SendNotification** — Criar notificação para usuário | `users.notifications` |
| 36 | 🟡 P2 | `[QRY]` | **ListUnreadNotifications** — Notificações não lidas | `users.notifications` |
| 37 | 🟡 P2 | `[CMD]` | **MarkNotificationAsRead** — Marcar como lida | `users.notifications` |
| 38 | 🟡 P2 | `[CMD]` | **UpdateNotificationPreferences** — Configurar canais | `users.notification_preferences` |
| 39 | 🟡 P2 | `[QRY]` | **GetNotificationPreferences** — Consultar preferências | `users.notification_preferences` |
| 40 | 🟡 P2 | `[CMD]` | **RevokeSpecificSession** — Revogar sessão por ID | `users.sessions` |
| 41 | 🟢 P3 | `[QRY]` | **GetLoginHistory** — Histórico de acessos | `users.login_history` |
| 42 | 🟢 P3 | `[QRY]` | **ListActiveSessions** — Sessões ativas do usuário | `users.sessions` |
| 43 | 🟢 P3 | `[CMD]` | **MarkAllNotificationsAsRead** — Marcar todas como lidas | `users.notifications` |
| 44 | 🟢 P3 | `[CMD]` | **DeleteNotification** — Remover notificação | `users.notifications` |

---

## Fase 2 — Catálogo

> Sem produtos não há o que vender. Implementar CRUD completo.

### 2.1 Categorias

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 45 | 🔴 P0 | `[CMD]` | ✅ **CreateCategory** — Criar categoria com hierarquia | `catalog.categories` |
| 46 | 🔴 P0 | `[CMD]` | ✅ **UpdateCategory** — Editar nome, slug, imagem | `catalog.categories` |
| 47 | 🔴 P0 | `[QRY]` | **GetCategory** — Consultar categoria individual | `catalog.categories` |
| 48 | 🔴 P0 | `[QRY]` | ✅ **ListCategories** — Árvore de categorias ativas | `catalog.categories` |
| 49 | 🟠 P1 | `[CMD]` | **ReorderCategories** — Alterar sort_order | `catalog.categories` |
| 50 | 🟠 P1 | `[CMD]` | **DeactivateCategory** — Soft delete | `catalog.categories` |
| 51 | 🟡 P2 | `[QRY]` | **GetCategoryBreadcrumb** — Caminho hierárquico (via path) | `catalog.categories` |

### 2.2 Marcas

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 52 | 🔴 P0 | `[CMD]` | ✅ **CreateBrand** — Cadastrar marca | `catalog.brands` |
| 53 | 🔴 P0 | `[CMD]` | ✅ **UpdateBrand** — Editar marca | `catalog.brands` |
| 54 | 🔴 P0 | `[QRY]` | **GetBrand** — Consultar marca individual | `catalog.brands` |
| 55 | 🔴 P0 | `[QRY]` | ✅ **ListBrands** — Listar marcas ativas | `catalog.brands` |
| 56 | 🟠 P1 | `[CMD]` | **DeactivateBrand** — Soft delete de marca | `catalog.brands` |

### 2.3 Produtos

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 57 | 🔴 P0 | `[CMD]` | ✅ **CreateProduct** — Cadastrar produto (DRAFT) | `catalog.products`, `catalog.product_images` |
| 58 | 🔴 P0 | `[CMD]` | ✅ **UpdateProduct** — Editar dados do produto | `catalog.products` |
| 59 | 🔴 P0 | `[CMD]` | **PublishProduct** — Status DRAFT → ACTIVE | `catalog.products` |
| 60 | 🔴 P0 | `[CMD]` | **PauseProduct** — Status ACTIVE → PAUSED | `catalog.products` |
| 61 | 🔴 P0 | `[CMD]` | **ArchiveProduct** — Soft delete / ARCHIVED | `catalog.products` |
| 62 | 🔴 P0 | `[QRY]` | ✅ **GetProduct** — Detalhe do produto (público) | `catalog.products`, `catalog.product_images`, `catalog.categories`, `catalog.brands` |
| 63 | 🔴 P0 | `[QRY]` | ✅ **SearchProducts** — Busca com filtros (nome, categoria, preço, atributos) | `catalog.products` (trigram + GIN) |
| 64 | 🔴 P0 | `[QRY]` | ✅ **ListProductsByCategory** — Produtos de uma categoria | `catalog.products`, `catalog.categories` |
| 65 | 🟠 P1 | `[CMD]` | **ManageProductImages** — Upload, reordenar, definir primária | `catalog.product_images` |
| 66 | 🟠 P1 | `[QRY]` | **ListFeaturedProducts** — Produtos em destaque | `catalog.products` |
| 67 | 🟠 P1 | `[QRY]` | **ListProductsByBrand** — Produtos filtrados por marca | `catalog.products`, `catalog.brands` |
| 68 | 🟡 P2 | `[CMD]` | **RestoreProduct** — Reativar produto (ARCHIVED → DRAFT) | `catalog.products` |
| 69 | 🟡 P2 | `[QRY]` | **ListProductsByTag** — Buscar por tags (GIN index) | `catalog.products` |

### 2.4 Estoque

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 70 | 🔴 P0 | `[CMD]` | ✅ **AdjustStock** — Entrada/saída manual de estoque | `catalog.products`, `catalog.stock_movements` |
| 71 | 🔴 P0 | `[CMD]` | **ReserveStock** — Reservar estoque (SELECT FOR UPDATE) | `catalog.products`, `catalog.stock_reservations`, `catalog.stock_movements` |
| 72 | 🔴 P0 | `[CMD]` | **ReleaseStock** — Liberar reserva expirada/cancelada | `catalog.products`, `catalog.stock_reservations`, `catalog.stock_movements` |
| 73 | 🔴 P0 | `[CMD]` | **ConfirmStockDeduction** — Deduzir estoque após pagamento | `catalog.products`, `catalog.stock_reservations`, `catalog.stock_movements` |
| 74 | 🟠 P1 | `[QRY]` | ✅ **GetStockMovements** — Histórico de movimentações | `catalog.stock_movements` |
| 75 | 🟠 P1 | `[QRY]` | **ListLowStockProducts** — Alerta de estoque baixo | `catalog.products` |
| 76 | 🟡 P2 | `[JOB]` | **CleanupExpiredReservations** — Liberar reservas expiradas | `catalog.stock_reservations`, `catalog.products` |

### 2.5 Reviews e Favoritos

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 77 | 🟡 P2 | `[CMD]` | **SubmitProductReview** — Avaliar produto comprado | `catalog.product_reviews` |
| 78 | 🟡 P2 | `[QRY]` | **ListProductReviews** — Reviews aprovados de um produto | `catalog.product_reviews` |
| 79 | 🟡 P2 | `[CMD]` | **ApproveReview** — Moderação (admin) | `catalog.product_reviews` |
| 80 | 🟡 P2 | `[CMD]` | **RespondToReview** — Resposta do vendedor | `catalog.product_reviews` |
| 81 | 🟡 P2 | `[CMD]` | **AddToFavorites** — Adicionar à wishlist | `catalog.user_favorites` |
| 82 | 🟡 P2 | `[CMD]` | **RemoveFromFavorites** — Remover da wishlist | `catalog.user_favorites` |
| 83 | 🟡 P2 | `[QRY]` | **ListFavorites** — Wishlist do usuário | `catalog.user_favorites` |
| 84 | 🟡 P2 | `[CMD]` | **DeleteReview** — Soft delete de review (deleted_at) | `catalog.product_reviews` |
| 85 | 🟡 P2 | `[QRY]` | **ListUserReviews** — Reviews feitos pelo usuário | `catalog.product_reviews` |
| 86 | 🟡 P2 | `[QRY]` | **GetProductReviewSummary** — Média de rating e contagem | `catalog.product_reviews` |

---

## Fase 3 — Carrinho

> Usuário precisa montar o pedido antes de pagar.

### 3.1 Carrinho Principal

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 87 | 🔴 P0 | `[CMD]` | **AddItemToCart** — Adicionar produto ao carrinho | `cart.carts`, `cart.items`, `catalog.products`, `cart.activity_log` |
| 88 | 🔴 P0 | `[CMD]` | **UpdateCartItemQuantity** — Alterar quantidade | `cart.items`, `cart.activity_log` |
| 89 | 🔴 P0 | `[CMD]` | **RemoveItemFromCart** — Soft delete (removed_at) | `cart.items`, `cart.activity_log` |
| 90 | 🔴 P0 | `[QRY]` | **GetCart** — Carrinho completo com preços atuais | `cart.carts`, `cart.items`, `catalog.products` |
| 91 | 🟠 P1 | `[CMD]` | **MergeGuestCart** — Unir carrinho anônimo ao usuário logado. **Estratégia de conflito:** se o mesmo produto existir nos dois carrinhos, somar as quantidades respeitando o estoque disponível; em caso de estoque insuficiente para a soma, usar a maior quantidade viável; o carrinho guest é marcado como `CONVERTED` ao final. | `cart.carts`, `cart.items` |
| 92 | 🟠 P1 | `[CMD]` | **ClearCart** — Esvaziar carrinho | `cart.items` |
| 93 | 🟡 P2 | `[EVT]` | **DetectPriceChange** — Atualizar current_price quando produto mudar | `cart.items` |
| 94 | 🟡 P2 | `[QRY]` | **GetCartActivityLog** — Consultar log de atividades | `cart.activity_log` |

### 3.2 Carrinho Salvo e Expiração

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 95 | 🟠 P1 | `[JOB]` | **ExpireAbandonedCarts** — Marcar carrinhos expirados | `cart.carts` |
| 96 | 🟡 P2 | `[CMD]` | **SaveCartForLater** — Salvar snapshot do carrinho | `cart.saved_carts` |
| 97 | 🟡 P2 | `[CMD]` | **RestoreSavedCart** — Restaurar carrinho salvo | `cart.saved_carts`, `cart.carts`, `cart.items` |
| 98 | 🟡 P2 | `[QRY]` | **ListSavedCarts** — Listar carrinhos salvos | `cart.saved_carts` |
| 99 | 🟡 P2 | `[CMD]` | **DeleteSavedCart** — Remover carrinho salvo | `cart.saved_carts` |

---

## Fase 4 — Pedidos e Checkout

> O core da operação: converter carrinho em pedido.

### 4.1 Checkout

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 100 | 🔴 P0 | `[CMD]` | **InitiateCheckout** — Validar carrinho, reservar estoque, calcular totais | `cart.carts`, `cart.items`, `catalog.products`, `catalog.stock_reservations`, `users.addresses` |
| 101 | 🔴 P0 | `[CMD]` | **SelectShippingMethod** — Escolher método de envio | (cálculo em memória, persiste no pedido) |
| 102 | 🔴 P0 | `[CMD]` | **PlaceOrder** — Criar pedido a partir do carrinho | `orders.orders`, `orders.items`, `cart.carts`, `catalog.stock_movements` |
| 103 | 🔴 P0 | `[QRY]` | **CalculateOrderTotals** — Subtotal, desconto, frete, imposto, total | (cálculo em memória) |

### 4.2 Gestão de Pedidos

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 104 | 🔴 P0 | `[QRY]` | **GetOrder** — Detalhe de um pedido | `orders.orders`, `orders.items`, `orders.status_history` |
| 105 | 🔴 P0 | `[QRY]` | **ListUserOrders** — Pedidos do usuário (paginado) | `orders.orders` |
| 106 | 🔴 P0 | `[CMD]` | **ConfirmOrder** — PENDING → CONFIRMED (após pagamento) | `orders.orders`, `orders.status_history` |
| 107 | 🔴 P0 | `[CMD]` | **CancelOrder** — Cancelar pedido (com motivo); deve liberar estoque reservado e reverter uso de cupom se aplicável | `orders.orders`, `orders.status_history`, `catalog.stock_movements` |
| 108 | 🟠 P1 | `[CMD]` | **ProcessOrder** — CONFIRMED → PROCESSING | `orders.orders`, `orders.status_history` |
| 109 | 🟠 P1 | `[CMD]` | **ShipOrder** — PROCESSING → SHIPPED (com tracking) | `orders.orders`, `orders.status_history` |
| 110 | 🟠 P1 | `[CMD]` | **DeliverOrder** — SHIPPED → DELIVERED | `orders.orders`, `orders.status_history` |
| 111 | 🟠 P1 | `[QRY]` | **GetOrderStatusHistory** — Timeline do pedido | `orders.status_history` |
| 112 | 🟠 P1 | `[QRY]` | **ListOrdersByStatus** — Pedidos por status (admin) | `orders.orders` |
| 113 | 🟠 P1 | `[QRY]` | **ListAllOrders** — Todos os pedidos paginado (admin) | `orders.orders` |
| 114 | 🟡 P2 | `[QRY]` | **SearchOrders** — Buscar pedidos por número, data, valor | `orders.orders` |
| 115 | 🟡 P2 | `[CMD]` | **AddOrderInternalNotes** — Adicionar notas internas (admin) | `orders.orders` |

### 4.3 Rastreamento e NF-e

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 116 | 🟠 P1 | `[CMD]` | **AddTrackingEvent** — Registrar evento de rastreio | `orders.tracking_events` |
| 117 | 🟠 P1 | `[QRY]` | **GetTrackingEvents** — Timeline de rastreamento | `orders.tracking_events` |
| 118 | 🟠 P1 | `[CMD]` | **IssueInvoice** — Emitir NF-e | `orders.invoices` |
| 119 | 🟠 P1 | `[QRY]` | **GetInvoice** — Consultar/baixar NF-e | `orders.invoices` |

### 4.4 Reembolsos

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 120 | 🟠 P1 | `[CMD]` | **RequestOrderRefund** — Solicitar reembolso. Cria `orders.refunds` com `payment_id = NULL` e status `PENDING`; o campo `payment_id` **deve ser preenchido** em **ProcessOrderRefund** (#121) antes de acionar o gateway, pois a FK para `payments.payments` é opcional apenas na criação. | `orders.refunds`, `orders.orders` |
| 121 | 🟠 P1 | `[CMD]` | **ProcessOrderRefund** — Aprovar/rejeitar reembolso; se aprovado: identificar o pagamento CAPTURED do pedido, preencher `payment_id`, acionar **RefundPayment** (#139) e atualizar estoque se devolução física | `orders.refunds`, `orders.orders`, `payments.payments`, `catalog.stock_movements` |
| 122 | 🟠 P1 | `[QRY]` | **ListOrderRefunds** — Reembolsos de um pedido | `orders.refunds` |

---

## Fase 5 — Pagamentos

> Integração com gateway. Implementar junto com Fase 4.

### 5.1 Métodos de Pagamento Salvos

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 123 | 🟠 P1 | `[CMD]` | **SavePaymentMethod** — Tokenizar cartão no gateway | `payments.user_methods` |
| 124 | 🟠 P1 | `[CMD]` | **DeletePaymentMethod** — Soft delete | `payments.user_methods` |
| 125 | 🟠 P1 | `[CMD]` | **SetDefaultPaymentMethod** — Marcar como padrão | `payments.user_methods` |
| 126 | 🟠 P1 | `[QRY]` | **GetPaymentMethod** — Consultar método individual | `payments.user_methods` |
| 127 | 🟠 P1 | `[QRY]` | **ListPaymentMethods** — Métodos salvos do usuário | `payments.user_methods` |

### 5.2 Processamento de Pagamento

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 128 | 🔴 P0 | `[CMD]` | **CreatePayment** — Iniciar pagamento (PIX, boleto ou cartão) | `payments.payments`, `payments.transactions` |
| 129 | 🔴 P0 | `[CMD]` | **AuthorizePayment** — Autorizar no gateway | `payments.payments`, `payments.transactions` |
| 130 | 🔴 P0 | `[CMD]` | **CapturePayment** — Capturar pagamento autorizado. O index `uq_payments_captured_per_order` no schema previne dupla captura no banco; a camada de aplicação deve tratar a violação de unicidade como operação idempotente (retornar o pagamento já capturado). | `payments.payments`, `payments.transactions` |
| 131 | 🔴 P0 | `[CMD]` | **HandlePaymentFailure** — Tratar falha do gateway | `payments.payments`, `payments.transactions` |
| 132 | 🔴 P0 | `[EVT]` | **HandlePaymentWebhook** — Processar callback do gateway (ver #146/#147) | `payments.webhooks`, `payments.payments`, `payments.transactions`, `orders.orders` |
| 133 | 🟠 P1 | `[CMD]` | **VoidPayment** — Estorno pré-captura (void) | `payments.payments`, `payments.transactions` |
| 134 | 🟠 P1 | `[QRY]` | **GetPayment** — Consultar status do pagamento | `payments.payments`, `payments.transactions` |
| 135 | 🟡 P2 | `[QRY]` | **GeneratePixQRCode** — Gerar QR Code PIX | `payments.payments` |
| 136 | 🟡 P2 | `[QRY]` | **GenerateBoleto** — Gerar boleto | `payments.payments` |
| 137 | 🟡 P2 | `[QRY]` | **ListPayments** — Listar pagamentos (admin) | `payments.payments` |
| 138 | 🟡 P2 | `[QRY]` | **GetPaymentTransactions** — Histórico de transações | `payments.transactions` |

### 5.3 Reembolsos e Chargebacks

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 139 | 🟠 P1 | `[CMD]` | **RefundPayment** — Processar reembolso no gateway | `payments.refunds`, `payments.transactions`, `payments.payments` |
| 140 | 🟠 P1 | `[CMD]` | **HandleChargeback** — Registrar chargeback recebido | `payments.chargebacks`, `payments.payments` |
| 141 | 🟡 P2 | `[CMD]` | **SubmitChargebackEvidence** — Enviar evidência de contestação | `payments.chargebacks` |
| 142 | 🟡 P2 | `[JOB]` | **CancelExpiredPayments** — Cancelar PIX/boleto expirados | `payments.payments` |
| 143 | 🟡 P2 | `[QRY]` | **ListChargebacks** — Listar chargebacks abertos | `payments.chargebacks` |
| 144 | 🟡 P2 | `[QRY]` | **GetChargeback** — Detalhe de um chargeback | `payments.chargebacks` |
| 145 | 🟡 P2 | `[CMD]` | **ResolveChargeback** — Marcar resultado (WON/LOST) | `payments.chargebacks` |

### 5.4 Webhooks

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 146 | 🔴 P0 | `[CMD]` | **ReceiveWebhook** — Gravar webhook cru (inbox pattern). **Idempotência obrigatória:** verificar `gateway_event_id` antes de inserir; se já existir, retornar 200 sem gravar novamente (gateways como Stripe e Asaas reenviam em falha). O index `uq_webhooks_gateway_event` no schema previne duplicatas no banco. | `payments.webhooks` |
| 147 | 🔴 P0 | `[JOB]` | **ProcessPendingWebhooks** — Fila de processamento; marcar `processed = TRUE` e `processed_at` ao concluir; em caso de erro incrementar `error_message` e reagendar | `payments.webhooks`, `payments.payments` |

---

## Fase 6 — Cupons de Desconto

> Implementar após o fluxo básico de compra funcionar.

### 6.1 Gestão de Cupons (Admin)

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 148 | 🟠 P1 | `[CMD]` | **CreateCoupon** — Criar cupom com regras | `coupons.coupons` |
| 149 | 🟠 P1 | `[CMD]` | **UpdateCoupon** — Editar valores e validade | `coupons.coupons` |
| 150 | 🟠 P1 | `[CMD]` | **ActivateCoupon** — DRAFT → ACTIVE | `coupons.coupons` |
| 151 | 🟠 P1 | `[CMD]` | **PauseCoupon** — ACTIVE → PAUSED | `coupons.coupons` |
| 152 | 🟠 P1 | `[CMD]` | **SetCouponEligibility** — Definir categorias/produtos/usuários elegíveis | `coupons.eligible_categories`, `coupons.eligible_products`, `coupons.eligible_users` |
| 153 | 🟠 P1 | `[QRY]` | **ListCoupons** — Cupons com filtros (admin) | `coupons.coupons` |
| 154 | 🟠 P1 | `[QRY]` | **GetCoupon** — Consultar cupom individual | `coupons.coupons` |
| 155 | 🟠 P1 | `[CMD]` | **DeactivateCoupon** — Soft delete de cupom | `coupons.coupons` |

### 6.2 Aplicação no Checkout

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 156 | 🟠 P1 | `[CMD]` | **ApplyCouponToCart** — Validar e aplicar cupom no carrinho | `coupons.coupons`, `cart.carts`, `coupons.reservations`, `coupons.usages` |
| 157 | 🟠 P1 | `[CMD]` | **RemoveCouponFromCart** — Remover cupom do carrinho | `cart.carts`, `coupons.reservations` |
| 158 | 🟠 P1 | `[CMD]` | **ValidateCoupon** — Verificar elegibilidade (status, período, limites, escopo) | `coupons.coupons`, `coupons.usages`, `coupons.eligible_*` |
| 159 | 🟠 P1 | `[CMD]` | **ConfirmCouponUsage** — Registrar uso após pedido confirmado | `coupons.usages`, `coupons.coupons` |
| 160 | 🟠 P1 | `[JOB]` | **ExpireCoupons** — ACTIVE → EXPIRED quando valid_until < NOW() | `coupons.coupons` |
| 161 | 🟠 P1 | `[CMD]` | **RevertCouponUsage** — Reverter uso em caso de cancelamento de pedido | `coupons.usages`, `coupons.coupons` |
| 162 | 🟡 P2 | `[QRY]` | **GetCouponUsageHistory** — Relatório de usos do cupom | `coupons.usages` |
| 163 | 🟡 P2 | `[JOB]` | **CleanupExpiredCouponReservations** — Liberar reservas expiradas | `coupons.reservations` |
| 164 | 🟢 P3 | `[JOB]` | **ExpireDepletedCoupons** — ACTIVE → DEPLETED quando current_uses = max_uses | `coupons.coupons` |

---

## Fase 7 — Infraestrutura e Operações

> Event-driven, auditoria e manutenção.

### 7.1 Domain Events (Outbox Pattern)

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 165 | 🟠 P1 | `[CMD]` | **PublishDomainEvent** — Persistir evento no outbox | `shared.domain_events` |
| 166 | 🟠 P1 | `[JOB]` | **ProcessDomainEvents** — Polling de eventos pendentes; usar `shared.processed_events` para garantir idempotência no consumo | `shared.domain_events`, `shared.processed_events` |
| 167 | 🟡 P2 | `[JOB]` | **RetryFailedEvents** — Reprocessar eventos com erro (retry_count < limite) | `shared.domain_events` |
| 168 | 🟢 P3 | `[JOB]` | **ArchiveProcessedEvents** — Mover para partição antiga / storage frio | `shared.domain_events` |

### 7.2 Auditoria

| # | Prioridade | Tipo | Use Case | Tabelas envolvidas |
|---|:---:|:---:|----------|-------------------|
| 169 | 🟡 P2 | `[CMD]` | **LogAuditEvent** — Registrar ação do usuário | `shared.audit_logs` |
| 170 | 🟡 P2 | `[QRY]` | **GetEntityAuditTrail** — Histórico de mudanças de uma entidade | `shared.audit_logs` |
| 171 | 🟡 P2 | `[QRY]` | **GetUserAuditTrail** — Ações de um usuário | `shared.audit_logs` |

---

## Resumo por Módulo e Prioridade

| Módulo | 🔴 P0 | 🟠 P1 | 🟡 P2 | 🟢 P3 | Total |
|--------|:---:|:---:|:---:|:---:|:---:|
| Users | 12 | 9 | 13 | 10 | **44** |
| Catalog | 20 | 8 | 14 | — | **42** |
| Cart | 4 | 3 | 6 | — | **13** |
| Orders | 8 | 13 | 2 | — | **23** |
| Payments | 7 | 9 | 9 | — | **25** |
| Coupons | — | 14 | 2 | 1 | **17** |
| Shared | — | 2 | 4 | 1 | **7** |
| **Total** | **51** | **58** | **50** | **12** | **171** |

---

## Ordem de Implementação Sugerida

```
Sprint 1 (MVP)  → Users P0 (1-5, 15-21) + Catalog P0 (45-48, 52-55, 57-64, 70-73)
Sprint 2 (MVP)  → Cart P0 (87-90) + Orders P0 (100-107) + Payments P0 (128-132, 146-147)
Sprint 3 (Core) → Users P1 (6-8, 22, 25-29) + Catalog P1 (49-50, 56, 65-67, 74-75)
Sprint 4 (Core) → Orders P1 (108-113, 116-122) + Payments P1 (123-127, 133-134, 139-140)
Sprint 5 (Core) → Coupons P1 (148-161) + Shared P1 (165-166)
Sprint 6 (UX)   → Cart P2 (93-99) + Reviews P2 (77-86) + Notifications P2 (35-40)
Sprint 7 (Ops)  → Shared P2 (167, 169-171) + Jobs (13-14, 76, 95, 142, 163)
Sprint 8 (Adv)  → Social login (9) + 2FA (10-11) + Chargebacks (141, 143-145) + P3 items
```

---

## Changelog de Correções Aplicadas

| # | Tipo | Descrição |
|---|:---:|-----------|
| C1 | Schema | `CREATE SCHEMA` adicionados para todos os namespaces (`users`, `catalog`, `cart`, `orders`, `payments`, `coupons`, `shared`) |
| C2 | Blocker | Função `orders.generate_order_number()` criada antes de `orders.orders` |
| C3 | Blocker | Partições filhas criadas para todas as tabelas com `PARTITION BY RANGE`: `users.login_history`, `catalog.stock_movements`, `payments.webhooks`, `shared.domain_events`, `shared.audit_logs` (cobertura 2025–2026, trimestral) |
| C4 | Alto | `payments.webhooks` recebeu coluna `gateway_event_id VARCHAR(100)` e index único `uq_webhooks_gateway_event(gateway_name, gateway_event_id)` para garantir idempotência contra reenvios do gateway |
| C5 | Alto | `payments.payments` recebeu index único parcial `uq_payments_captured_per_order(order_id) WHERE status = 'CAPTURED'` para prevenir dupla captura |
| C6 | Médio | `catalog.product_reviews`: removida a constraint `UNIQUE (product_id, user_id, order_id)` que não funcionava corretamente com soft delete e `NULL`; substituída por dois indexes parciais que excluem `deleted_at IS NOT NULL` em ambos os casos (com e sem `order_id`) |
| C7 | Médio | UC #91 **MergeGuestCart**: estratégia de merge de conflito explicitada (somar quantidades, respeitar estoque, converter carrinho guest) |
| C8 | Médio | UC #120/#121: fluxo de `payment_id` em `orders.refunds` documentado — criado como NULL, obrigatoriamente preenchido antes do acionamento do gateway em ProcessOrderRefund |
| C9 | Médio | UC #146 **ReceiveWebhook**: requisito de idempotência via `gateway_event_id` explicitado |
| C10 | Médio | UC #130 **CapturePayment**: comportamento esperado diante da violação de `uq_payments_captured_per_order` documentado |