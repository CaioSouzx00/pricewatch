CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    name          VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE products (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR(255)  NOT NULL,
    url           TEXT          NOT NULL,
    store         VARCHAR(120),
    image_url     TEXT,
    currency      CHAR(3)       NOT NULL DEFAULT 'BRL',
    current_price NUMERIC(12, 2),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_products_user_id ON products (user_id);

CREATE TABLE price_history (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    price      NUMERIC(12, 2) NOT NULL,
    checked_at TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_price_history_product_checked ON price_history (product_id, checked_at DESC);

CREATE TABLE alerts (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id   BIGINT         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    target_price NUMERIC(12, 2) NOT NULL,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    triggered_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_alerts_user_id ON alerts (user_id);
CREATE INDEX idx_alerts_product_active ON alerts (product_id, active);
