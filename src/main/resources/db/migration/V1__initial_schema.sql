-- ============================================================
-- V1: Initial schema for Trading Bot
-- ============================================================

CREATE TABLE users (
    id                 BIGSERIAL PRIMARY KEY,
    username           VARCHAR(50) NOT NULL UNIQUE,
    password           VARCHAR(255) NOT NULL,
    is_active          BOOLEAN NOT NULL DEFAULT FALSE,
    role               VARCHAR(20) NOT NULL DEFAULT 'USER',
    alpaca_api_key     VARCHAR(512),
    alpaca_api_secret  VARCHAR(512),
    alpaca_base_url    VARCHAR(200) DEFAULT 'https://paper-api.alpaca.markets',
    trade_amount       NUMERIC(19,4) NOT NULL DEFAULT 500.0000,
    range_value        NUMERIC(19,4) NOT NULL DEFAULT 0.0100,
    profit_percent     NUMERIC(19,4) NOT NULL DEFAULT 6.0000,
    daily_loss_limit   NUMERIC(19,4) DEFAULT 1000.0000,
    trading_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);

CREATE TABLE trade_orders (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_order_id   VARCHAR(64) NOT NULL UNIQUE,
    symbol            VARCHAR(20) NOT NULL,
    qty               INT NOT NULL,
    entry_price       NUMERIC(19,4),
    trade_amount      NUMERIC(19,4),
    profit_percent    NUMERIC(19,4),
    stop_price        NUMERIC(19,4),
    limit_price       NUMERIC(19,4),
    take_profit       NUMERIC(19,4),
    stop_loss         NUMERIC(19,4),
    alpaca_order_id   VARCHAR(100),
    order_status      VARCHAR(50),
    raw_response      TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP
);

CREATE INDEX idx_orders_user_created ON trade_orders(user_id, created_at DESC);
CREATE INDEX idx_orders_user_symbol  ON trade_orders(user_id, symbol);
CREATE INDEX idx_orders_alpaca_id    ON trade_orders(alpaca_order_id);
CREATE UNIQUE INDEX idx_orders_client_order_id ON trade_orders(client_order_id);
