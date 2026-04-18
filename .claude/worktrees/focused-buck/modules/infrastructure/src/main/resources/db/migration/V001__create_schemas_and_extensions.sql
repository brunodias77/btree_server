-- V001: Create schemas, extensions, and UUID v7 function.

-- Schemas
CREATE SCHEMA IF NOT EXISTS shared;
CREATE SCHEMA IF NOT EXISTS users;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS cart;
CREATE SCHEMA IF NOT EXISTS orders;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS coupons;

-- Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- UUID v7 generator (time-ordered UUIDs, RFC 9562)
CREATE OR REPLACE FUNCTION uuid_generate_v7() RETURNS UUID AS $$
DECLARE
    unix_ts_ms BIGINT;
    uuid_bytes BYTEA;
    hex_str    TEXT;
BEGIN
    unix_ts_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
    uuid_bytes := gen_random_bytes(16);
    -- Embed 48-bit Unix timestamp in ms into the first 6 bytes
    -- Apply & 255 as BIGINT before casting to INT to avoid overflow
    uuid_bytes := SET_BYTE(uuid_bytes, 0, ((unix_ts_ms >> 40) & 255)::INT);
    uuid_bytes := SET_BYTE(uuid_bytes, 1, ((unix_ts_ms >> 32) & 255)::INT);
    uuid_bytes := SET_BYTE(uuid_bytes, 2, ((unix_ts_ms >> 24) & 255)::INT);
    uuid_bytes := SET_BYTE(uuid_bytes, 3, ((unix_ts_ms >> 16) & 255)::INT);
    uuid_bytes := SET_BYTE(uuid_bytes, 4, ((unix_ts_ms >>  8) & 255)::INT);
    uuid_bytes := SET_BYTE(uuid_bytes, 5,  (unix_ts_ms        & 255)::INT);
    -- Set version 7 (bits 12-15 of byte 6)
    uuid_bytes := SET_BYTE(uuid_bytes, 6, (GET_BYTE(uuid_bytes, 6) & 15) | 112);
    -- Set variant 10xx (bits 6-7 of byte 8)
    uuid_bytes := SET_BYTE(uuid_bytes, 8, (GET_BYTE(uuid_bytes, 8) & 63) | 128);
    hex_str := encode(uuid_bytes, 'hex');
    RETURN (
        SUBSTRING(hex_str FROM  1 FOR  8) || '-' ||
        SUBSTRING(hex_str FROM  9 FOR  4) || '-' ||
        SUBSTRING(hex_str FROM 13 FOR  4) || '-' ||
        SUBSTRING(hex_str FROM 17 FOR  4) || '-' ||
        SUBSTRING(hex_str FROM 21 FOR 12)
    )::UUID;
END
$$ LANGUAGE plpgsql VOLATILE;
