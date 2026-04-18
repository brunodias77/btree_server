-- =============================================================
-- V011__seed_data.sql
-- Seed de dados de desenvolvimento
--
-- Senhas (todas criptografadas via pgcrypto bcrypt):
--   admin@btree.com   → Admin@123
--   demais usuários   → Senha@123
-- =============================================================

DO $$
DECLARE
  -- Roles
  v_role_admin     UUID;
  v_role_customer  UUID;

  -- Users
  v_user_admin     UUID;
  v_user_joao      UUID;
  v_user_maria     UUID;
  v_user_carlos    UUID;

  -- Brands
  v_brand_apple    UUID;
  v_brand_samsung  UUID;
  v_brand_nike     UUID;
  v_brand_adidas   UUID;
  v_brand_penguin  UUID;

  -- Categories
  v_cat_eletronicos   UUID;
  v_cat_smartphones   UUID;
  v_cat_notebooks     UUID;
  v_cat_moda          UUID;
  v_cat_camisetas     UUID;
  v_cat_tenis         UUID;
  v_cat_livros        UUID;

  -- Products
  v_prod_iphone15pro    UUID;
  v_prod_iphone14       UUID;
  v_prod_galaxy_s24     UUID;
  v_prod_macbook        UUID;
  v_prod_galaxy_book    UUID;
  v_prod_nike_airmax    UUID;
  v_prod_nike_camiseta  UUID;
  v_prod_adi_ultra      UUID;
  v_prod_adi_camiseta   UUID;
  v_prod_clean_code     UUID;
  v_prod_ddd            UUID;

  -- Coupons
  v_coupon_welcome  UUID;
  v_coupon_frete    UUID;
  v_coupon_tech     UUID;

BEGIN

  -- ===========================================================
  -- ROLES
  -- ===========================================================
  SELECT uuid_generate_v7() INTO v_role_admin;
  SELECT uuid_generate_v7() INTO v_role_customer;

  INSERT INTO users.roles (id, name, description) VALUES
    (v_role_admin,    'ROLE_ADMIN',    'Administrador do sistema'),
    (v_role_customer, 'ROLE_CUSTOMER', 'Cliente da loja');

  -- ===========================================================
  -- USERS
  -- ===========================================================
  SELECT uuid_generate_v7() INTO v_user_admin;
  SELECT uuid_generate_v7() INTO v_user_joao;
  SELECT uuid_generate_v7() INTO v_user_maria;
  SELECT uuid_generate_v7() INTO v_user_carlos;

  INSERT INTO users.users (id, username, email, email_verified, password_hash, enabled) VALUES
    (v_user_admin,  'admin',           'admin@btree.com',            true, crypt('Admin@123', gen_salt('bf', 10)), true),
    (v_user_joao,   'joao.silva',      'joao.silva@email.com',       true, crypt('Senha@123', gen_salt('bf', 10)), true),
    (v_user_maria,  'maria.santos',    'maria.santos@email.com',     true, crypt('Senha@123', gen_salt('bf', 10)), true),
    (v_user_carlos, 'carlos.oliveira', 'carlos.oliveira@email.com',  true, crypt('Senha@123', gen_salt('bf', 10)), true);

  INSERT INTO users.user_roles (user_id, role_id) VALUES
    (v_user_admin,  v_role_admin),
    (v_user_admin,  v_role_customer),
    (v_user_joao,   v_role_customer),
    (v_user_maria,  v_role_customer),
    (v_user_carlos, v_role_customer);

  -- ===========================================================
  -- PROFILES
  -- ===========================================================
  INSERT INTO users.profiles (id, user_id, first_name, last_name, display_name, birth_date, gender, cpf, newsletter_subscribed, accepted_terms_at, accepted_privacy_at) VALUES
    (uuid_generate_v7(), v_user_admin,  'Admin',   'Btree',    'Admin',          '1990-01-01', 'MALE',   '000.000.000-00', false, NOW(), NOW()),
    (uuid_generate_v7(), v_user_joao,   'João',    'Silva',    'João Silva',     '1992-05-15', 'MALE',   '111.222.333-44', true,  NOW(), NOW()),
    (uuid_generate_v7(), v_user_maria,  'Maria',   'Santos',   'Maria Santos',   '1988-11-22', 'FEMALE', '555.666.777-88', true,  NOW(), NOW()),
    (uuid_generate_v7(), v_user_carlos, 'Carlos',  'Oliveira', 'Carlos Oliveira','1995-03-08', 'MALE',   '999.888.777-66', false, NOW(), NOW());

  -- ===========================================================
  -- NOTIFICATION PREFERENCES
  -- ===========================================================
  INSERT INTO users.notification_preferences (id, user_id) VALUES
    (uuid_generate_v7(), v_user_admin),
    (uuid_generate_v7(), v_user_joao),
    (uuid_generate_v7(), v_user_maria),
    (uuid_generate_v7(), v_user_carlos);

  -- ===========================================================
  -- ADDRESSES
  -- ===========================================================
  INSERT INTO users.addresses (id, user_id, label, recipient_name, street, number, neighborhood, city, state, postal_code, is_default, is_billing_address) VALUES
    (uuid_generate_v7(), v_user_joao,   'Casa',     'João Silva',      'Rua das Flores',   '123', 'Jardim Paulista', 'São Paulo',      'SP', '01310-100', true,  true),
    (uuid_generate_v7(), v_user_joao,   'Trabalho', 'João Silva',      'Av. Paulista',     '900', 'Bela Vista',      'São Paulo',      'SP', '01311-000', false, false),
    (uuid_generate_v7(), v_user_maria,  'Casa',     'Maria Santos',    'Rua Copacabana',   '456', 'Copacabana',      'Rio de Janeiro', 'RJ', '22020-001', true,  true),
    (uuid_generate_v7(), v_user_carlos, 'Casa',     'Carlos Oliveira', 'Av. Afonso Pena',  '789', 'Centro',          'Belo Horizonte', 'MG', '30130-005', true,  true);

  -- ===========================================================
  -- USER PAYMENT METHODS
  -- ===========================================================
  INSERT INTO payments.user_payment_methods (id, user_id, method_type, card_brand, card_last_four, card_holder_name, card_expiry_month, card_expiry_year, gateway_token, is_default) VALUES
    (uuid_generate_v7(), v_user_joao,   'CREDIT_CARD', 'VISA',       '4242', 'JOAO SILVA',      12, 2027, 'tok_visa_joao_1',    true),
    (uuid_generate_v7(), v_user_joao,   'CREDIT_CARD', 'MASTERCARD', '5353', 'JOAO SILVA',       6, 2026, 'tok_mc_joao_2',      false),
    (uuid_generate_v7(), v_user_maria,  'CREDIT_CARD', 'ELO',        '6363', 'MARIA SANTOS',     9, 2028, 'tok_elo_maria_1',    true),
    (uuid_generate_v7(), v_user_carlos, 'DEBIT_CARD',  'VISA',       '7474', 'CARLOS OLIVEIRA',  3, 2026, 'tok_deb_carlos_1',   true);

  -- ===========================================================
  -- BRANDS
  -- ===========================================================
  SELECT uuid_generate_v7() INTO v_brand_apple;
  SELECT uuid_generate_v7() INTO v_brand_samsung;
  SELECT uuid_generate_v7() INTO v_brand_nike;
  SELECT uuid_generate_v7() INTO v_brand_adidas;
  SELECT uuid_generate_v7() INTO v_brand_penguin;

  INSERT INTO catalog.brands (id, name, slug, description) VALUES
    (v_brand_apple,   'Apple',         'apple',         'Tecnologia inovadora com design premium.'),
    (v_brand_samsung, 'Samsung',       'samsung',       'Eletrônicos e tecnologia para todos os públicos.'),
    (v_brand_nike,    'Nike',          'nike',          'Just Do It — moda esportiva e calçados de alto desempenho.'),
    (v_brand_adidas,  'Adidas',        'adidas',        'Impossible is Nothing — esportivo, estilo e conforto.'),
    (v_brand_penguin, 'Penguin Books', 'penguin-books', 'Editora global com os melhores títulos de tecnologia e negócios.');

  -- ===========================================================
  -- CATEGORIES (hierarquia)
  -- ===========================================================
  SELECT uuid_generate_v7() INTO v_cat_eletronicos;
  SELECT uuid_generate_v7() INTO v_cat_smartphones;
  SELECT uuid_generate_v7() INTO v_cat_notebooks;
  SELECT uuid_generate_v7() INTO v_cat_moda;
  SELECT uuid_generate_v7() INTO v_cat_camisetas;
  SELECT uuid_generate_v7() INTO v_cat_tenis;
  SELECT uuid_generate_v7() INTO v_cat_livros;

  INSERT INTO catalog.categories (id, parent_id, name, slug, description, sort_order, active) VALUES
    (v_cat_eletronicos, NULL,              'Eletrônicos', 'eletronicos', 'Smartphones, notebooks e muito mais.',                    1, true),
    (v_cat_smartphones, v_cat_eletronicos, 'Smartphones', 'smartphones', 'Os melhores celulares do mercado.',                       1, true),
    (v_cat_notebooks,   v_cat_eletronicos, 'Notebooks',   'notebooks',   'Computadores portáteis para trabalho e entretenimento.',  2, true),
    (v_cat_moda,        NULL,              'Moda',        'moda',        'Roupas, calçados e acessórios.',                          2, true),
    (v_cat_camisetas,   v_cat_moda,        'Camisetas',   'camisetas',   'Camisetas esportivas e casuais.',                         1, true),
    (v_cat_tenis,       v_cat_moda,        'Tênis',       'tenis',       'Calçados para corrida, treino e uso casual.',             2, true),
    (v_cat_livros,      NULL,              'Livros',      'livros',      'Livros de tecnologia, negócios e muito mais.',            3, true);

  -- ===========================================================
  -- PRODUCTS
  -- ===========================================================
  SELECT uuid_generate_v7() INTO v_prod_iphone15pro;
  SELECT uuid_generate_v7() INTO v_prod_iphone14;
  SELECT uuid_generate_v7() INTO v_prod_galaxy_s24;
  SELECT uuid_generate_v7() INTO v_prod_macbook;
  SELECT uuid_generate_v7() INTO v_prod_galaxy_book;
  SELECT uuid_generate_v7() INTO v_prod_nike_airmax;
  SELECT uuid_generate_v7() INTO v_prod_nike_camiseta;
  SELECT uuid_generate_v7() INTO v_prod_adi_ultra;
  SELECT uuid_generate_v7() INTO v_prod_adi_camiseta;
  SELECT uuid_generate_v7() INTO v_prod_clean_code;
  SELECT uuid_generate_v7() INTO v_prod_ddd;

  INSERT INTO catalog.products
    (id, category_id, brand_id, name, slug, description, short_description, sku,
     price, compare_at_price, cost_price, quantity, low_stock_threshold, weight,
     status, featured)
  VALUES
    -- Smartphones
    (v_prod_iphone15pro, v_cat_smartphones, v_brand_apple,
     'iPhone 15 Pro', 'iphone-15-pro',
     'Chip A17 Pro de 3 nm, câmera de 48 MP com zoom óptico 5×, carcaça em titânio grau aeronáutico.',
     'O iPhone mais avançado com chip A17 Pro e câmera profissional.',
     'APPL-IP15P-001', 8999.00, 9999.00, 6500.00, 50, 5, 0.187, 'ACTIVE', true),

    (v_prod_iphone14, v_cat_smartphones, v_brand_apple,
     'iPhone 14', 'iphone-14',
     'Chip A15 Bionic, câmera de 12 MP, modo Ação e SOS Emergência via satélite.',
     'iPhone sólido com ótima relação custo-benefício.',
     'APPL-IP14-001', 5999.00, 6499.00, 4200.00, 80, 8, 0.172, 'ACTIVE', false),

    (v_prod_galaxy_s24, v_cat_smartphones, v_brand_samsung,
     'Samsung Galaxy S24', 'samsung-galaxy-s24',
     'Snapdragon 8 Gen 3, câmera de 50 MP, Galaxy AI integrado e tela Dynamic AMOLED 2X 120 Hz.',
     'O flagship Android da Samsung com inteligência artificial nativa.',
     'SAMS-GS24-001', 6499.00, 6999.00, 4800.00, 60, 6, 0.167, 'ACTIVE', true),

    -- Notebooks
    (v_prod_macbook, v_cat_notebooks, v_brand_apple,
     'MacBook Pro M3 14"', 'macbook-pro-m3-14',
     'Chip M3 de próxima geração, 18 GB de RAM unificada, SSD de 512 GB e tela Liquid Retina XDR de 14".',
     'Performance profissional em corpo ultrafino.',
     'APPL-MBP14-M3-001', 18999.00, 19999.00, 13500.00, 20, 2, 1.550, 'ACTIVE', true),

    (v_prod_galaxy_book, v_cat_notebooks, v_brand_samsung,
     'Samsung Galaxy Book3 Pro', 'samsung-galaxy-book3-pro',
     'Intel Core i7 de 13ª geração, 16 GB RAM, SSD de 512 GB e tela AMOLED 14" 120 Hz.',
     'Notebook premium Samsung com tela AMOLED vibrante.',
     'SAMS-GB3P-001', 7999.00, 8499.00, 5600.00, 30, 3, 1.170, 'ACTIVE', false),

    -- Camisetas
    (v_prod_nike_camiseta, v_cat_camisetas, v_brand_nike,
     'Nike Camiseta Dri-FIT Run', 'nike-camiseta-dri-fit-run',
     'Tecido Dri-FIT de alto desempenho que evita o acúmulo de suor durante os treinos mais intensos.',
     'Leve e respirável para treinos de corrida.',
     'NIKE-CAM-DRFT-001', 149.90, 189.90, 60.00, 200, 20, 0.150, 'ACTIVE', false),

    (v_prod_adi_camiseta, v_cat_camisetas, v_brand_adidas,
     'Adidas Camiseta Essentials', 'adidas-camiseta-essentials',
     '100% algodão penteado, corte relaxado e bordado Adidas no peito. Disponível em diversas cores.',
     'Conforto e estilo para o dia a dia.',
     'ADID-CAM-ESS-001', 129.90, 159.90, 50.00, 250, 25, 0.180, 'ACTIVE', false),

    -- Tênis
    (v_prod_nike_airmax, v_cat_tenis, v_brand_nike,
     'Nike Air Max 270', 'nike-air-max-270',
     'Amortecimento Max Air de 270° na parte traseira para conforto máximo o dia todo. Cabedal em mesh respirável.',
     'Estilo retrô com o máximo de conforto Air da Nike.',
     'NIKE-AM270-001', 799.90, 999.90, 350.00, 80, 10, 0.310, 'ACTIVE', true),

    (v_prod_adi_ultra, v_cat_tenis, v_brand_adidas,
     'Adidas Ultraboost 22', 'adidas-ultraboost-22',
     'Tecnologia Boost de retorno de energia + cabedal Primeknit+ que se adapta ao pé durante a corrida.',
     'O tênis favorito dos corredores de rua.',
     'ADID-UB22-001', 899.90, 1099.90, 400.00, 60, 6, 0.340, 'ACTIVE', true),

    -- Livros
    (v_prod_clean_code, v_cat_livros, v_brand_penguin,
     'Clean Code', 'clean-code',
     'Robert C. Martin apresenta as melhores práticas de escrita de código limpo, legível e fácil de manter.',
     'O guia definitivo para escrever código de qualidade.',
     'PEN-CLNCD-001', 89.90, 99.90, 30.00, 150, 15, 0.450, 'ACTIVE', false),

    (v_prod_ddd, v_cat_livros, v_brand_penguin,
     'Domain-Driven Design', 'domain-driven-design',
     'Eric Evans explica como modelar software complexo alinhando o design ao domínio do negócio com DDD.',
     'A bíblia do design orientado a domínio.',
     'PEN-DDD-001', 99.90, 119.90, 35.00, 100, 10, 0.520, 'ACTIVE', false);

  -- ===========================================================
  -- PRODUCT IMAGES
  -- ===========================================================
  INSERT INTO catalog.product_images (id, product_id, url, alt_text, sort_order, is_primary) VALUES
    (uuid_generate_v7(), v_prod_iphone15pro,  'https://images.btree.com/products/iphone-15-pro-1.jpg',       'iPhone 15 Pro — Titânio Natural — Frente',    1, true),
    (uuid_generate_v7(), v_prod_iphone15pro,  'https://images.btree.com/products/iphone-15-pro-2.jpg',       'iPhone 15 Pro — Sistema de câmera pró',        2, false),
    (uuid_generate_v7(), v_prod_iphone14,     'https://images.btree.com/products/iphone-14-1.jpg',           'iPhone 14 — Meia-noite',                       1, true),
    (uuid_generate_v7(), v_prod_galaxy_s24,   'https://images.btree.com/products/galaxy-s24-1.jpg',          'Samsung Galaxy S24 — Titanium Black',          1, true),
    (uuid_generate_v7(), v_prod_galaxy_s24,   'https://images.btree.com/products/galaxy-s24-2.jpg',          'Samsung Galaxy S24 — Traseira com câmera',     2, false),
    (uuid_generate_v7(), v_prod_macbook,      'https://images.btree.com/products/macbook-pro-m3-1.jpg',      'MacBook Pro M3 14" — Cinza-espacial',          1, true),
    (uuid_generate_v7(), v_prod_macbook,      'https://images.btree.com/products/macbook-pro-m3-2.jpg',      'MacBook Pro M3 — Teclado e trackpad',          2, false),
    (uuid_generate_v7(), v_prod_galaxy_book,  'https://images.btree.com/products/galaxy-book3-1.jpg',        'Samsung Galaxy Book3 Pro — Graphite',          1, true),
    (uuid_generate_v7(), v_prod_nike_airmax,  'https://images.btree.com/products/nike-air-max-270-1.jpg',    'Nike Air Max 270 — Branco/Preto',              1, true),
    (uuid_generate_v7(), v_prod_nike_airmax,  'https://images.btree.com/products/nike-air-max-270-2.jpg',    'Nike Air Max 270 — Solado Air',                2, false),
    (uuid_generate_v7(), v_prod_nike_camiseta,'https://images.btree.com/products/nike-dri-fit-1.jpg',        'Nike Camiseta Dri-FIT Run — Preta',            1, true),
    (uuid_generate_v7(), v_prod_adi_ultra,    'https://images.btree.com/products/adidas-ultraboost22-1.jpg', 'Adidas Ultraboost 22 — Core Black',            1, true),
    (uuid_generate_v7(), v_prod_adi_ultra,    'https://images.btree.com/products/adidas-ultraboost22-2.jpg', 'Adidas Ultraboost 22 — Lateral',               2, false),
    (uuid_generate_v7(), v_prod_adi_camiseta, 'https://images.btree.com/products/adidas-essentials-1.jpg',   'Adidas Camiseta Essentials — Branco',          1, true),
    (uuid_generate_v7(), v_prod_clean_code,   'https://images.btree.com/products/clean-code-1.jpg',          'Clean Code — Robert C. Martin',                1, true),
    (uuid_generate_v7(), v_prod_ddd,          'https://images.btree.com/products/ddd-evans-1.jpg',           'Domain-Driven Design — Eric Evans',            1, true);

  -- ===========================================================
  -- STOCK MOVEMENTS (entrada inicial)
  -- ===========================================================
  INSERT INTO catalog.stock_movements (id, created_at, product_id, movement_type, quantity, reference_type, notes) VALUES
    (uuid_generate_v7(), NOW(), v_prod_iphone15pro,  'PURCHASE', 50,  'SEED', 'Estoque inicial'),
    (uuid_generate_v7(), NOW(), v_prod_iphone14,     'PURCHASE', 80,  'SEED', 'Estoque inicial'),
    (uuid_generate_v7(), NOW(), v_prod_galaxy_s24,   'PURCHASE', 60,  'SEED', 'Estoque inicial'),
    (uuid_generate_v7(), NOW(), v_prod_macbook,      'PURCHASE', 20,  'SEED', 'Estoque inicial'),
    (uuid_generate_v7(), NOW(), v_prod_galaxy_book,  'PURCHASE', 30,  'SEED', 'Estoque inicial'),
    (uuid_generate_v7(), NOW(), v_prod_nike_airmax,  'PURCHASE', 80,  'SEED', 'Estoque inicial'),
    (uuid_generate_v7(), NOW(), v_prod_nike_camiseta,'PURCHASE', 200, 'SEED', 'Estoque inicial'),
    (uuid_generate_v7(), NOW(), v_prod_adi_ultra,    'PURCHASE', 60,  'SEED', 'Estoque inicial'),
    (uuid_generate_v7(), NOW(), v_prod_adi_camiseta, 'PURCHASE', 250, 'SEED', 'Estoque inicial'),
    (uuid_generate_v7(), NOW(), v_prod_clean_code,   'PURCHASE', 150, 'SEED', 'Estoque inicial'),
    (uuid_generate_v7(), NOW(), v_prod_ddd,          'PURCHASE', 100, 'SEED', 'Estoque inicial');

  -- ===========================================================
  -- COUPONS
  -- ===========================================================
  SELECT uuid_generate_v7() INTO v_coupon_welcome;
  SELECT uuid_generate_v7() INTO v_coupon_frete;
  SELECT uuid_generate_v7() INTO v_coupon_tech;

  INSERT INTO coupons.coupons
    (id, code, description, coupon_type, coupon_scope, status,
     discount_value, min_order_value, max_discount_amount,
     max_uses, max_uses_per_user, current_uses, starts_at, expires_at)
  VALUES
    (v_coupon_welcome,
     'WELCOME10', '10% de desconto para novos clientes',
     'PERCENTAGE', 'ALL', 'ACTIVE',
     10.00, 0.00, 150.00,
     1000, 1, 0, NOW(), NOW() + INTERVAL '90 days'),

    (v_coupon_frete,
     'FRETE0', 'Frete grátis em compras acima de R$ 200',
     'FREE_SHIPPING', 'ALL', 'ACTIVE',
     1.00, 200.00, NULL,
     500, 3, 0, NOW(), NOW() + INTERVAL '60 days'),

    (v_coupon_tech,
     'TECH50', 'R$ 50 de desconto em Eletrônicos (mín. R$ 500)',
     'FIXED_AMOUNT', 'CATEGORY', 'ACTIVE',
     50.00, 500.00, NULL,
     300, 2, 0, NOW(), NOW() + INTERVAL '30 days');

  -- TECH50 elegível para toda a categoria Eletrônicos
  INSERT INTO coupons.eligible_categories (coupon_id, category_id) VALUES
    (v_coupon_tech, v_cat_eletronicos);

END;
$$;
