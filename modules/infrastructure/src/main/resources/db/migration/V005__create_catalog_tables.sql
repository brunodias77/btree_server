-- V005: Create catalog schema tables.

-- Brands (soft delete)
CREATE TABLE catalog.brands (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    name            VARCHAR(200)    NOT NULL,
    slug            VARCHAR(256)    NOT NULL UNIQUE,
    description     TEXT,
    logo_url        VARCHAR(512),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

-- Categories (soft delete, self-referencing hierarchy)
CREATE TABLE catalog.categories (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    parent_id       UUID            REFERENCES catalog.categories(id),
    name            VARCHAR(200)    NOT NULL,
    slug            VARCHAR(256)    NOT NULL UNIQUE,
    description     TEXT,
    image_url       VARCHAR(512),
    sort_order      INT             NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_categories_parent ON catalog.categories (parent_id);
CREATE INDEX idx_categories_slug ON catalog.categories (slug) WHERE deleted_at IS NULL;

-- Products (soft delete, optimistic locking)
CREATE TABLE catalog.products (
    id              UUID                    PRIMARY KEY DEFAULT uuid_generate_v7(),
    category_id     UUID                    REFERENCES catalog.categories(id),
    brand_id        UUID                    REFERENCES catalog.brands(id),
    name            VARCHAR(300)            NOT NULL,
    slug            VARCHAR(350)            NOT NULL UNIQUE,
    description     TEXT,
    short_description VARCHAR(500),
    sku             VARCHAR(50)             NOT NULL UNIQUE,
    price           DECIMAL(10,2)           NOT NULL,
    compare_at_price DECIMAL(10,2),
    cost_price      DECIMAL(10,2),
    quantity         INT                    NOT NULL DEFAULT 0,
    low_stock_threshold INT                 NOT NULL DEFAULT 5,
    weight          DECIMAL(8,3),
    width           DECIMAL(8,2),
    height          DECIMAL(8,2),
    depth           DECIMAL(8,2),
    status          shared.product_status   NOT NULL DEFAULT 'DRAFT',
    featured        BOOLEAN                 NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    version         INT                     NOT NULL DEFAULT 1,
    CONSTRAINT chk_products_price CHECK (price >= 0),
    CONSTRAINT chk_products_quantity CHECK (quantity >= 0)
);

CREATE INDEX idx_products_category ON catalog.products (category_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_brand ON catalog.products (brand_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_slug ON catalog.products (slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_sku ON catalog.products (sku) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_status ON catalog.products (status) WHERE deleted_at IS NULL;

-- Product Images
CREATE TABLE catalog.product_images (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    product_id      UUID            NOT NULL REFERENCES catalog.products(id) ON DELETE CASCADE,
    url             VARCHAR(512)    NOT NULL,
    alt_text        VARCHAR(256),
    sort_order      INT             NOT NULL DEFAULT 0,
    is_primary      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_images_product ON catalog.product_images (product_id);

-- Stock Movements — partitioned by quarter
CREATE TABLE catalog.stock_movements (
    id              UUID                        NOT NULL DEFAULT uuid_generate_v7(),
    created_at      TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    product_id      UUID                        NOT NULL,
    movement_type   shared.stock_movement_type  NOT NULL,
    quantity        INT                         NOT NULL,
    reference_id    UUID,
    reference_type  VARCHAR(50),
    notes           TEXT,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE catalog.stock_movements_2026_q1 PARTITION OF catalog.stock_movements
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE catalog.stock_movements_2026_q2 PARTITION OF catalog.stock_movements
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE catalog.stock_movements_2026_q3 PARTITION OF catalog.stock_movements
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE catalog.stock_movements_2026_q4 PARTITION OF catalog.stock_movements
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

CREATE INDEX idx_stock_movements_product ON catalog.stock_movements (product_id);

-- Stock Reservations
CREATE TABLE catalog.stock_reservations (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    product_id      UUID            NOT NULL REFERENCES catalog.products(id),
    order_id        UUID,
    quantity        INT             NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    confirmed       BOOLEAN         NOT NULL DEFAULT FALSE,
    released        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_reservations_product ON catalog.stock_reservations (product_id) WHERE released = FALSE AND confirmed = FALSE;
CREATE INDEX idx_stock_reservations_expires ON catalog.stock_reservations (expires_at) WHERE released = FALSE AND confirmed = FALSE;

-- Product Reviews (soft delete)
CREATE TABLE catalog.product_reviews (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    product_id      UUID            NOT NULL REFERENCES catalog.products(id),
    user_id         UUID            NOT NULL,
    rating          INT             NOT NULL,
    title           VARCHAR(200),
    comment         TEXT,
    verified_purchase BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_product_reviews_product ON catalog.product_reviews (product_id) WHERE deleted_at IS NULL;

-- User Favorites
CREATE TABLE catalog.user_favorites (
    user_id     UUID            NOT NULL,
    product_id  UUID            NOT NULL REFERENCES catalog.products(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, product_id)
);
