-- V008: Create payments schema tables.

-- User Payment Methods (soft delete, optimistic locking)
CREATE TABLE payments.user_payment_methods (
    id                  UUID                        PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id             UUID                        NOT NULL,
    method_type         shared.payment_method_type  NOT NULL,
    card_brand          shared.card_brand,
    card_last_four      VARCHAR(4),
    card_holder_name    VARCHAR(200),
    card_expiry_month   INT,
    card_expiry_year    INT,
    gateway_token       VARCHAR(256),
    is_default          BOOLEAN                     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    version             INT                         NOT NULL DEFAULT 1
);

CREATE INDEX idx_user_payment_methods_user ON payments.user_payment_methods (user_id) WHERE deleted_at IS NULL;

-- Payments (optimistic locking)
CREATE TABLE payments.payments (
    id                      UUID                    PRIMARY KEY DEFAULT uuid_generate_v7(),
    order_id                UUID                    NOT NULL UNIQUE,
    user_id                 UUID                    NOT NULL,
    payment_method_id       UUID                    REFERENCES payments.user_payment_methods(id),
    method_type             shared.payment_method_type NOT NULL,
    status                  shared.payment_status   NOT NULL DEFAULT 'PENDING',
    amount                  DECIMAL(10,2)           NOT NULL,
    currency                VARCHAR(3)              NOT NULL DEFAULT 'BRL',
    gateway_payment_id      VARCHAR(256),
    gateway_status          VARCHAR(100),
    failure_reason          TEXT,
    authorized_at           TIMESTAMPTZ,
    captured_at             TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    version                 INT                     NOT NULL DEFAULT 1,
    CONSTRAINT chk_payments_amount CHECK (amount > 0)
);

CREATE INDEX idx_payments_order ON payments.payments (order_id);
CREATE INDEX idx_payments_user ON payments.payments (user_id);
CREATE INDEX idx_payments_status ON payments.payments (status);

-- Payment Transactions
CREATE TABLE payments.transactions (
    id                  UUID                        PRIMARY KEY DEFAULT uuid_generate_v7(),
    payment_id          UUID                        NOT NULL REFERENCES payments.payments(id),
    transaction_type    shared.transaction_type     NOT NULL,
    amount              DECIMAL(10,2)               NOT NULL,
    gateway_transaction_id VARCHAR(256),
    status              VARCHAR(50)                 NOT NULL,
    response_code       VARCHAR(20),
    response_message    TEXT,
    created_at          TIMESTAMPTZ                 NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_payment ON payments.transactions (payment_id);

-- Payment Refunds
CREATE TABLE payments.payment_refunds (
    id                  UUID                    PRIMARY KEY DEFAULT uuid_generate_v7(),
    payment_id          UUID                    NOT NULL REFERENCES payments.payments(id),
    amount              DECIMAL(10,2)           NOT NULL,
    reason              TEXT                    NOT NULL,
    status              shared.refund_status    NOT NULL DEFAULT 'PENDING',
    gateway_refund_id   VARCHAR(256),
    refunded_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ             NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_refunds_payment ON payments.payment_refunds (payment_id);

-- Chargebacks
CREATE TABLE payments.chargebacks (
    id                  UUID                        PRIMARY KEY DEFAULT uuid_generate_v7(),
    payment_id          UUID                        NOT NULL REFERENCES payments.payments(id),
    amount              DECIMAL(10,2)               NOT NULL,
    reason              TEXT                        NOT NULL,
    status              shared.chargeback_status    NOT NULL DEFAULT 'OPENED',
    gateway_chargeback_id VARCHAR(256),
    evidence_url        VARCHAR(512),
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ                 NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ                 NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chargebacks_payment ON payments.chargebacks (payment_id);

-- Webhooks — partitioned by quarter (idempotency via gateway_event_id)
CREATE TABLE payments.webhooks (
    id                  UUID            NOT NULL DEFAULT uuid_generate_v7(),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    gateway             VARCHAR(50)     NOT NULL,
    gateway_event_id    VARCHAR(256)    NOT NULL,
    event_type          VARCHAR(100)    NOT NULL,
    raw_payload         JSONB           NOT NULL,
    processed           BOOLEAN         NOT NULL DEFAULT FALSE,
    processed_at        TIMESTAMPTZ,
    error_message       TEXT,
    retry_count         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE payments.webhooks_2026_q1 PARTITION OF payments.webhooks
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE payments.webhooks_2026_q2 PARTITION OF payments.webhooks
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE payments.webhooks_2026_q3 PARTITION OF payments.webhooks
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE payments.webhooks_2026_q4 PARTITION OF payments.webhooks
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

-- Partitioned tables require all partition columns in unique indexes
CREATE UNIQUE INDEX uq_webhooks_gateway_event ON payments.webhooks (gateway, gateway_event_id, created_at);
CREATE INDEX idx_webhooks_pending ON payments.webhooks (created_at ASC) WHERE processed = FALSE;
