-- V004: Create users schema tables.

-- Roles
CREATE TABLE users.roles (
    id              UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    name            VARCHAR(50)     NOT NULL UNIQUE,
    description     VARCHAR(256),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

INSERT INTO users.roles (name, description) VALUES
    ('admin', 'Administrator with full access'),
    ('customer', 'Default customer role'),
    ('support', 'Customer support agent');

-- Users
CREATE TABLE users.users (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    username                VARCHAR(256)    NOT NULL,
    email                   VARCHAR(256)    NOT NULL,
    email_verified          BOOLEAN         NOT NULL DEFAULT FALSE,
    password_hash           TEXT,
    phone_number            VARCHAR(50),
    phone_number_verified   BOOLEAN         NOT NULL DEFAULT FALSE,
    two_factor_enabled      BOOLEAN         NOT NULL DEFAULT FALSE,
    two_factor_secret       TEXT,
    account_locked          BOOLEAN         NOT NULL DEFAULT FALSE,
    lock_expires_at         TIMESTAMPTZ,
    access_failed_count     INT             NOT NULL DEFAULT 0,
    enabled                 BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version                 INT             NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX uq_users_username ON users.users (LOWER(username));
CREATE UNIQUE INDEX uq_users_email ON users.users (LOWER(email));
CREATE INDEX idx_users_enabled ON users.users (enabled) WHERE enabled = TRUE;

-- User-Role join table
CREATE TABLE users.user_roles (
    user_id UUID NOT NULL REFERENCES users.users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES users.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Profiles (1:1 with users, soft delete)
CREATE TABLE users.profiles (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id                 UUID            NOT NULL UNIQUE REFERENCES users.users(id) ON DELETE CASCADE,
    first_name              VARCHAR(100),
    last_name               VARCHAR(100),
    display_name            VARCHAR(200),
    avatar_url              VARCHAR(512),
    birth_date              DATE,
    gender                  VARCHAR(20),
    cpf                     VARCHAR(14),
    preferred_language      VARCHAR(10)     NOT NULL DEFAULT 'pt-BR',
    preferred_currency      VARCHAR(3)      NOT NULL DEFAULT 'BRL',
    newsletter_subscribed   BOOLEAN         NOT NULL DEFAULT FALSE,
    accepted_terms_at       TIMESTAMPTZ,
    accepted_privacy_at     TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    version                 INT             NOT NULL DEFAULT 1,
    CONSTRAINT chk_profiles_cpf_format CHECK (cpf IS NULL OR cpf ~ '^\d{3}\.\d{3}\.\d{3}-\d{2}$')
);

CREATE INDEX idx_profiles_user_id ON users.profiles (user_id);

-- Notification Preferences (1:1 with users)
CREATE TABLE users.notification_preferences (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id                 UUID            NOT NULL UNIQUE REFERENCES users.users(id) ON DELETE CASCADE,
    email_enabled           BOOLEAN         NOT NULL DEFAULT TRUE,
    push_enabled            BOOLEAN         NOT NULL DEFAULT TRUE,
    sms_enabled             BOOLEAN         NOT NULL DEFAULT FALSE,
    order_updates           BOOLEAN         NOT NULL DEFAULT TRUE,
    promotions              BOOLEAN         NOT NULL DEFAULT TRUE,
    price_drops             BOOLEAN         NOT NULL DEFAULT TRUE,
    back_in_stock           BOOLEAN         NOT NULL DEFAULT TRUE,
    newsletter              BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_prefs_user_id ON users.notification_preferences (user_id);

-- Addresses (soft delete)
CREATE TABLE users.addresses (
    id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id             UUID            NOT NULL REFERENCES users.users(id) ON DELETE CASCADE,
    label               VARCHAR(50),
    recipient_name      VARCHAR(200),
    street              VARCHAR(256)    NOT NULL,
    number              VARCHAR(20),
    complement          VARCHAR(100),
    neighborhood        VARCHAR(100),
    city                VARCHAR(100)    NOT NULL,
    state               VARCHAR(2)         NOT NULL,
    postal_code         VARCHAR(9)      NOT NULL,
    country             VARCHAR(2)      NOT NULL DEFAULT 'BR',
    latitude            DECIMAL(10,7),
    longitude           DECIMAL(10,7),
    ibge_code           VARCHAR(7),
    is_default          BOOLEAN         NOT NULL DEFAULT FALSE,
    is_billing_address  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT chk_addresses_state CHECK (state ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_addresses_postal_code CHECK (postal_code ~ '^\d{5}-?\d{3}$')
);

CREATE INDEX idx_addresses_user_id ON users.addresses (user_id) WHERE deleted_at IS NULL;

-- Sessions
CREATE TABLE users.sessions (
    id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id             UUID            NOT NULL REFERENCES users.users(id) ON DELETE CASCADE,
    refresh_token_hash  VARCHAR(256)    NOT NULL,
    ip_address          VARCHAR(45),
    user_agent          TEXT,
    device_type         VARCHAR(50),
    expires_at          TIMESTAMPTZ     NOT NULL,
    revoked             BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version             INT             NOT NULL DEFAULT 1
);

CREATE INDEX idx_sessions_user_id ON users.sessions (user_id);
CREATE INDEX idx_sessions_active ON users.sessions (user_id) WHERE revoked = FALSE;

-- User Tokens
CREATE TABLE users.user_tokens (
    id              UUID                PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id         UUID                NOT NULL REFERENCES users.users(id) ON DELETE CASCADE,
    token_type      shared.token_type   NOT NULL,
    token_hash      VARCHAR(256)        NOT NULL,
    expires_at      TIMESTAMPTZ         NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_tokens_user_type ON users.user_tokens (user_id, token_type);

-- Login History — partitioned by quarter
CREATE TABLE users.login_history (
    id              UUID            NOT NULL DEFAULT uuid_generate_v7(),
    user_id         UUID,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    device_type     VARCHAR(50),
    success         BOOLEAN         NOT NULL,
    failure_reason  VARCHAR(200),
    attempted_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, attempted_at)
) PARTITION BY RANGE (attempted_at);

CREATE TABLE users.login_history_2026_q1 PARTITION OF users.login_history
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE users.login_history_2026_q2 PARTITION OF users.login_history
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE users.login_history_2026_q3 PARTITION OF users.login_history
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE users.login_history_2026_q4 PARTITION OF users.login_history
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

CREATE INDEX idx_login_history_user ON users.login_history (user_id);

-- Social Logins
CREATE TABLE users.social_logins (
    id                      UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id                 UUID            NOT NULL REFERENCES users.users(id) ON DELETE CASCADE,
    provider                VARCHAR(50)     NOT NULL,
    provider_user_id        VARCHAR(256)    NOT NULL,
    provider_display_name   VARCHAR(256),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_social_logins_user ON users.social_logins (user_id);

-- User Notifications
CREATE TABLE users.user_notifications (
    id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id             UUID            NOT NULL REFERENCES users.users(id) ON DELETE CASCADE,
    title               VARCHAR(200)    NOT NULL,
    message             TEXT            NOT NULL,
    notification_type   VARCHAR(50)     NOT NULL,
    reference_type      VARCHAR(50),
    reference_id        UUID,
    action_url          VARCHAR(512),
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_notifications_user ON users.user_notifications (user_id);
CREATE INDEX idx_user_notifications_unread ON users.user_notifications (user_id) WHERE read_at IS NULL;
