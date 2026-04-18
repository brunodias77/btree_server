-- V007: Create orders schema tables.

-- Order number generator
CREATE OR REPLACE FUNCTION orders.generate_order_number() RETURNS TEXT AS $$
DECLARE
    date_part TEXT;
    random_part TEXT;
BEGIN
    date_part := TO_CHAR(NOW(), 'YYYYMMDD');
    random_part := UPPER(SUBSTRING(MD5(uuid_generate_v7()::TEXT) FROM 1 FOR 9));
    RETURN 'ORD-' || date_part || '-' || random_part;
END
$$ LANGUAGE plpgsql VOLATILE;

-- Orders (optimistic locking)
CREATE TABLE orders.orders (
    id                  UUID                    PRIMARY KEY DEFAULT uuid_generate_v7(),
    order_number        VARCHAR(30)             NOT NULL UNIQUE DEFAULT orders.generate_order_number(),
    user_id             UUID                    NOT NULL,
    status              shared.order_status     NOT NULL DEFAULT 'PENDING',
    shipping_method     shared.shipping_method  NOT NULL,
    shipping_address_snapshot JSONB             NOT NULL,
    subtotal            DECIMAL(10,2)           NOT NULL,
    shipping_cost       DECIMAL(10,2)           NOT NULL DEFAULT 0,
    discount            DECIMAL(10,2)           NOT NULL DEFAULT 0,
    total               DECIMAL(10,2)           NOT NULL,
    coupon_code         VARCHAR(50),
    notes               TEXT,
    cancellation_reason shared.cancellation_reason,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    version             INT                     NOT NULL DEFAULT 1,
    CONSTRAINT chk_orders_total CHECK (total >= 0)
);

CREATE INDEX idx_orders_user ON orders.orders (user_id);
CREATE INDEX idx_orders_status ON orders.orders (status);
CREATE INDEX idx_orders_number ON orders.orders (order_number);

-- Order Items
CREATE TABLE orders.order_items (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    order_id        UUID            NOT NULL REFERENCES orders.orders(id) ON DELETE CASCADE,
    product_id      UUID            NOT NULL,
    product_name    VARCHAR(300)    NOT NULL,
    product_sku     VARCHAR(50)     NOT NULL,
    quantity        INT             NOT NULL,
    unit_price      DECIMAL(10,2)   NOT NULL,
    subtotal        DECIMAL(10,2)   NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order ON orders.order_items (order_id);

-- Order Status History
CREATE TABLE orders.order_status_history (
    id              UUID                PRIMARY KEY DEFAULT uuid_generate_v7(),
    order_id        UUID                NOT NULL REFERENCES orders.orders(id) ON DELETE CASCADE,
    from_status     shared.order_status,
    to_status       shared.order_status NOT NULL,
    reason          TEXT,
    changed_by      UUID,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_status_history_order ON orders.order_status_history (order_id);

-- Tracking Events
CREATE TABLE orders.tracking_events (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    order_id        UUID            NOT NULL REFERENCES orders.orders(id) ON DELETE CASCADE,
    carrier         VARCHAR(100),
    tracking_code   VARCHAR(100),
    status          VARCHAR(100)    NOT NULL,
    description     TEXT,
    location        VARCHAR(256),
    occurred_at     TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tracking_events_order ON orders.tracking_events (order_id);

-- Invoices
CREATE TABLE orders.invoices (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    order_id        UUID            NOT NULL REFERENCES orders.orders(id),
    invoice_number  VARCHAR(50)     NOT NULL UNIQUE,
    invoice_url     VARCHAR(512),
    xml_url         VARCHAR(512),
    issued_at       TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_order ON orders.invoices (order_id);

-- Order Refunds
CREATE TABLE orders.order_refunds (
    id              UUID                PRIMARY KEY DEFAULT uuid_generate_v7(),
    order_id        UUID                NOT NULL REFERENCES orders.orders(id),
    amount          DECIMAL(10,2)       NOT NULL,
    reason          TEXT                NOT NULL,
    status          shared.refund_status NOT NULL DEFAULT 'PENDING',
    refunded_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_refunds_order ON orders.order_refunds (order_id);
