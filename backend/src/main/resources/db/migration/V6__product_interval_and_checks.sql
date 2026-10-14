-- Adiciona intervalo de checagem customizado por produto e registro da ultima checagem
ALTER TABLE products ADD COLUMN check_interval_minutes INTEGER NOT NULL DEFAULT 60;
ALTER TABLE products ADD COLUMN last_checked_at TIMESTAMPTZ;

CREATE INDEX idx_products_active_schedule ON products (active, last_checked_at);
