-- ============================================
-- V2: Order tracking & status sync from IBKR
-- ============================================
-- Adds fields to track fill state and IBKR perm_id
-- Adds indices to enable fast lookup by IBKR order IDs
--   (used by IbkrOrderStatusListener when callbacks arrive)

ALTER TABLE trade_orders
    ADD COLUMN IF NOT EXISTS filled_qty       DECIMAL(15,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS remaining_qty    DECIMAL(15,4),
    ADD COLUMN IF NOT EXISTS avg_fill_price   DECIMAL(15,4),
    ADD COLUMN IF NOT EXISTS last_fill_price  DECIMAL(15,4),
    ADD COLUMN IF NOT EXISTS ibkr_status_raw  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS last_ibkr_update TIMESTAMP;

-- Critical: lookup orders by their IBKR order IDs (set in placeOrder)
-- Each TradeOrder row has 3 possible IBKR ids (parent / TP / SL)
-- so we need an index on each.
CREATE INDEX IF NOT EXISTS idx_trade_orders_parent_id
    ON trade_orders(ibkr_parent_order_id);

CREATE INDEX IF NOT EXISTS idx_trade_orders_tp_id
    ON trade_orders(ibkr_take_profit_order_id);

CREATE INDEX IF NOT EXISTS idx_trade_orders_sl_id
    ON trade_orders(ibkr_stop_loss_order_id);

-- Composite index for the most common query: "give me this user's open orders"
CREATE INDEX IF NOT EXISTS idx_trade_orders_user_status_created
    ON trade_orders(user_id, order_status, created_at DESC);
