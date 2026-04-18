-- Habilitar extensão trigram (necessária para GIN de texto)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Índice GIN trigram para busca por nome (case-insensitive)
CREATE INDEX IF NOT EXISTS idx_products_name_trgm
    ON catalog.products USING GIN (lower(name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- Índice GIN trigram para busca por SKU
CREATE INDEX IF NOT EXISTS idx_products_sku_trgm
    ON catalog.products USING GIN (lower(sku) gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- Índice GIN trigram para short_description (opcional, para buscas mais amplas)
CREATE INDEX IF NOT EXISTS idx_products_short_description_trgm
    ON catalog.products USING GIN (lower(short_description) gin_trgm_ops)
    WHERE deleted_at IS NULL;
