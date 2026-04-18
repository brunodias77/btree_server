-- V006: Create cart schema tables.

-- Carts (optimistic locking)
CREATE TABLE cart.carts (
    id              UUID                PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id         UUID,
    session_id      VARCHAR(256),
    status          shared.cart_status  NOT NULL DEFAULT 'ACTIVE',
    coupon_code     VARCHAR(50),
    shipping_method shared.shipping_method,
    notes           TEXT,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    version         INT                 NOT NULL DEFAULT 1
);

CREATE INDEX idx_carts_user ON cart.carts (user_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_carts_session ON cart.carts (session_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_carts_expires ON cart.carts (expires_at) WHERE status = 'ACTIVE';

-- Cart Items
CREATE TABLE cart.cart_items (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    cart_id         UUID            NOT NULL REFERENCES cart.carts(id) ON DELETE CASCADE,
    product_id      UUID            NOT NULL,
    quantity        INT             NOT NULL DEFAULT 1,
    unit_price      DECIMAL(10,2)   NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_cart_items_price CHECK (unit_price >= 0),
    UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart ON cart.cart_items (cart_id);

-- Cart Activity Log
CREATE TABLE cart.cart_activity_log (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    cart_id         UUID            NOT NULL REFERENCES cart.carts(id) ON DELETE CASCADE,
    action          VARCHAR(50)     NOT NULL,
    product_id      UUID,
    quantity        INT,
    details         JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cart_activity_cart ON cart.cart_activity_log (cart_id);

-- Saved Carts
CREATE TABLE cart.saved_carts (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id         UUID            NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    items           JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saved_carts_user ON cart.saved_carts (user_id);
