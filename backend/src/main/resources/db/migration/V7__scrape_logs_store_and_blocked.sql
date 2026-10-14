-- Adiciona loja e flag de bloqueio para observabilidade agregada do scraper
ALTER TABLE scrape_logs ADD COLUMN store VARCHAR(120);
ALTER TABLE scrape_logs ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_scrape_logs_store ON scrape_logs (store);
CREATE INDEX idx_scrape_logs_created_at ON scrape_logs (created_at DESC);
CREATE INDEX idx_scrape_logs_blocked ON scrape_logs (blocked);
