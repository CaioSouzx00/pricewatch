CREATE TABLE scrape_logs (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    success       BOOLEAN       NOT NULL,
    error_message TEXT,
    latency_ms    INTEGER,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_scrape_logs_product_id ON scrape_logs (product_id);
