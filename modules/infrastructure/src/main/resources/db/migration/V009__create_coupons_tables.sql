-- V009: Create coupons schema tables.

-- Coupons (soft delete, optimistic locking)
CREATE TABLE coupons.coupons (
    id                  UUID                    PRIMARY KEY DEFAULT uuid_generate_v7(),
    code                VARCHAR(50)             NOT NULL UNIQUE,
    description         TEXT,
    coupon_type         shared.coupon_type      NOT NULL,
    coupon_scope        shared.coupon_scope     NOT NULL DEFAULT 'ALL',
    status              shared.coupon_status    NOT NULL DEFAULT 'ACTIVE',
    discount_value      DECIMAL(10,2)           NOT NULL,
    min_order_value     DECIMAL(10,2),
    max_discount_amount DECIMAL(10,2),
    max_uses            INT,
    max_uses_per_user   INT                     NOT NULL DEFAULT 1,
    current_uses        INT                     NOT NULL DEFAULT 0,
    starts_at           TIMESTAMPTZ             NOT NULL,
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    version             INT                     NOT NULL DEFAULT 1,
    CONSTRAINT chk_coupons_discount CHECK (discount_value > 0)
);

CREATE INDEX idx_coupons_code ON coupons.coupons (code) WHERE deleted_at IS NULL;
CREATE INDEX idx_coupons_status ON coupons.coupons (status) WHERE deleted_at IS NULL;

-- Coupon Eligible Categories
CREATE TABLE coupons.eligible_categories (
    coupon_id   UUID    NOT NULL REFERENCES coupons.coupons(id) ON DELETE CASCADE,
    category_id UUID    NOT NULL,
    PRIMARY KEY (coupon_id, category_id)
);

-- Coupon Eligible Products
CREATE TABLE coupons.eligible_products (
    coupon_id   UUID    NOT NULL REFERENCES coupons.coupons(id) ON DELETE CASCADE,
    product_id  UUID    NOT NULL,
    PRIMARY KEY (coupon_id, product_id)
);

-- Coupon Eligible Brands
CREATE TABLE coupons.eligible_brands (
    coupon_id   UUID    NOT NULL REFERENCES coupons.coupons(id) ON DELETE CASCADE,
    brand_id    UUID    NOT NULL,
    PRIMARY KEY (coupon_id, brand_id)
);

-- Coupon Eligible Users
CREATE TABLE coupons.eligible_users (
    coupon_id   UUID    NOT NULL REFERENCES coupons.coupons(id) ON DELETE CASCADE,
    user_id     UUID    NOT NULL,
    PRIMARY KEY (coupon_id, user_id)
);

-- Coupon Usages
CREATE TABLE coupons.coupon_usages (
    id          UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    coupon_id   UUID            NOT NULL REFERENCES coupons.coupons(id),
    user_id     UUID            NOT NULL,
    order_id    UUID            NOT NULL,
    discount    DECIMAL(10,2)   NOT NULL,
    used_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coupon_usages_coupon ON coupons.coupon_usages (coupon_id);
CREATE INDEX idx_coupon_usages_user ON coupons.coupon_usages (coupon_id, user_id);

-- Coupon Reservations (temporary hold during checkout)
CREATE TABLE coupons.coupon_reservations (
    id          UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    coupon_id   UUID            NOT NULL REFERENCES coupons.coupons(id),
    user_id     UUID            NOT NULL,
    cart_id     UUID,
    expires_at  TIMESTAMPTZ     NOT NULL,
    confirmed   BOOLEAN         NOT NULL DEFAULT FALSE,
    released    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coupon_reservations_coupon ON coupons.coupon_reservations (coupon_id) WHERE confirmed = FALSE AND released = FALSE;
CREATE INDEX idx_coupon_reservations_expires ON coupons.coupon_reservations (expires_at) WHERE confirmed = FALSE AND released = FALSE;
