-- V010: Converte token_type de shared.token_type (enum nativo PG) para VARCHAR,
-- mantendo a constraint de valores válidos via CHECK.
-- Motivação: o driver JDBC/Hibernate não faz cast automático para enums nativos do PG,
-- o que impede inserts simples. VARCHAR com CHECK oferece a mesma garantia de integridade.

ALTER TABLE users.user_tokens
    ALTER COLUMN token_type TYPE VARCHAR(50) USING token_type::VARCHAR;

ALTER TABLE users.user_tokens
    ADD CONSTRAINT chk_user_tokens_token_type
    CHECK (token_type IN (
        'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'TWO_FACTOR',
        'TWO_FACTOR_SETUP', 'MAGIC_LINK', 'ACCOUNT_UNLOCK', 'PHONE_VERIFICATION'
    ));
