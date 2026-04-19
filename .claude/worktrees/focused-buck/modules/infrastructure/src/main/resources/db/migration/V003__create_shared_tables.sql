-- V003: Create shared outbox, audit, and processed events tables.

-- Domain Events (Outbox Pattern) — partitioned by quarter
CREATE TABLE shared.domain_events (
    id              UUID            NOT NULL DEFAULT uuid_generate_v7(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    module          VARCHAR(50)     NOT NULL,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    UUID            NOT NULL,
    event_type      VARCHAR(200)    NOT NULL,
    payload         JSONB           NOT NULL,
    processed_at    TIMESTAMPTZ,
    error_message   TEXT,
    retry_count     INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Create initial partitions (2026 Q1-Q4)
CREATE TABLE shared.domain_events_2026_q1 PARTITION OF shared.domain_events
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE shared.domain_events_2026_q2 PARTITION OF shared.domain_events
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE shared.domain_events_2026_q3 PARTITION OF shared.domain_events
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE shared.domain_events_2026_q4 PARTITION OF shared.domain_events
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

CREATE INDEX idx_domain_events_pending ON shared.domain_events (created_at ASC) WHERE processed_at IS NULL;
CREATE INDEX idx_domain_events_aggregate ON shared.domain_events (aggregate_type, aggregate_id);
CREATE INDEX idx_domain_events_module_pending ON shared.domain_events (module) WHERE processed_at IS NULL;

-- Processed Events (idempotency — id IS the processed event UUID)
CREATE TABLE shared.processed_events (
    id              UUID            PRIMARY KEY,
    event_type      VARCHAR(200)    NOT NULL,
    module          VARCHAR(50)     NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Audit Logs — partitioned by quarter
CREATE TABLE shared.audit_logs (
    id              UUID            NOT NULL DEFAULT uuid_generate_v7(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    module          VARCHAR(50)     NOT NULL,
    user_id         UUID,
    action          VARCHAR(100)    NOT NULL,
    entity_type     VARCHAR(100)    NOT NULL,
    entity_id       UUID            NOT NULL,
    old_values      JSONB,
    new_values      JSONB,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE shared.audit_logs_2026_q1 PARTITION OF shared.audit_logs
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE shared.audit_logs_2026_q2 PARTITION OF shared.audit_logs
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE shared.audit_logs_2026_q3 PARTITION OF shared.audit_logs
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE shared.audit_logs_2026_q4 PARTITION OF shared.audit_logs
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

CREATE INDEX idx_audit_logs_user ON shared.audit_logs (user_id);
CREATE INDEX idx_audit_logs_entity ON shared.audit_logs (entity_type, entity_id);
