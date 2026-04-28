-- ============================================
-- Trading Bot - Multi-User IBKR Schema
-- ============================================

CREATE TABLE IF NOT EXISTS users (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(50) UNIQUE NOT NULL,
    password            VARCHAR(255) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT FALSE,
    role                VARCHAR(20) NOT NULL DEFAULT 'USER',

    -- IBKR Connection Settings (each user has their own VPS)
    ibkr_host           VARCHAR(255),                    -- VPS IP, e.g. "45.32.123.45"
    ibkr_port           INTEGER DEFAULT 4002,            -- 4002=Paper, 4001=Live (IB Gateway)
    ibkr_client_id      INTEGER DEFAULT 1,               -- Client ID inside Gateway (usually 1)
    ibkr_account_id     VARCHAR(20),                     -- e.g. "U1234567" or "DU1234567" (Paper)
    ibkr_paper_trading  BOOLEAN NOT NULL DEFAULT TRUE,   -- safety: default to paper

    -- Trading Settings
    trade_amount        DECIMAL(15,2) DEFAULT 500.00,
    range_value         DECIMAL(10,4) DEFAULT 0.01,
    profit_percent      DECIMAL(5,2) DEFAULT 6.00,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_active ON users(is_active);

-- ============================================
CREATE TABLE IF NOT EXISTS trade_orders (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    symbol                   VARCHAR(20) NOT NULL,
    qty                      INTEGER NOT NULL,

    entry_price              DECIMAL(15,4) NOT NULL,
    trade_amount             DECIMAL(15,2) NOT NULL,
    profit_percent           DECIMAL(5,2) NOT NULL,

    stop_price               DECIMAL(15,4),
    limit_price              DECIMAL(15,4),
    take_profit              DECIMAL(15,4),
    stop_loss                DECIMAL(15,4) NOT NULL,

    -- IBKR specific (3 linked orders for bracket)
    ibkr_parent_order_id     INTEGER,
    ibkr_take_profit_order_id INTEGER,
    ibkr_stop_loss_order_id  INTEGER,
    ibkr_perm_id             BIGINT,

    order_status             VARCHAR(50) DEFAULT 'PENDING',

    -- Idempotency
    client_order_id          VARCHAR(100) UNIQUE,

    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP
);

CREATE INDEX idx_trade_orders_user_id ON trade_orders(user_id);
CREATE INDEX idx_trade_orders_symbol ON trade_orders(symbol);
CREATE INDEX idx_trade_orders_created_at ON trade_orders(created_at DESC);
CREATE INDEX idx_trade_orders_status ON trade_orders(order_status);
