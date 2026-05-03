package com.mod.trading.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "trade_orders")
public class TradeOrder {

    /**
     * Internal lifecycle states stored in {@code order_status}.
     * <p>
     * Flow:
     * PENDING  → row inserted, IBKR not called yet
     * SUBMITTED → IBKR accepted the bracket
     * FAILED_SUBMIT → IBKR call threw
     * PRESUBMITTED / FILLED / PARTIALLY_FILLED / CANCELLED / REJECTED → from IBKR callback
     * PENDING_CANCEL → cancelOrder called, awaiting confirmation
     */
    public static final class Status {
        public static final String PENDING            = "PENDING";
        public static final String SUBMITTED          = "SUBMITTED";
        public static final String FAILED_SUBMIT      = "FAILED_SUBMIT";
        public static final String PRESUBMITTED       = "PRESUBMITTED";
        public static final String FILLED             = "FILLED";
        public static final String PARTIALLY_FILLED   = "PARTIALLY_FILLED";
        public static final String PENDING_CANCEL     = "PENDING_CANCEL";
        public static final String CANCELLED          = "CANCELLED";
        public static final String REJECTED           = "REJECTED";

        private Status() {}

        public static boolean isTerminal(String s) {
            return FILLED.equals(s) || CANCELLED.equals(s) || REJECTED.equals(s)
                    || FAILED_SUBMIT.equals(s);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private Integer qty;

    @Column(name = "entry_price", precision = 15, scale = 4, nullable = false)
    private BigDecimal entryPrice;

    @Column(name = "trade_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal tradeAmount;

    @Column(name = "profit_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal profitPercent;

    @Column(name = "stop_price", precision = 15, scale = 4)
    private BigDecimal stopPrice;

    @Column(name = "limit_price", precision = 15, scale = 4)
    private BigDecimal limitPrice;

    @Column(name = "take_profit", precision = 15, scale = 4)
    private BigDecimal takeProfit;

    @Column(name = "stop_loss", precision = 15, scale = 4, nullable = false)
    private BigDecimal stopLoss;

    // ===== IBKR specific =====
    // Bracket order = 3 linked orders
    @Column(name = "ibkr_parent_order_id")
    private Integer ibkrParentOrderId;

    @Column(name = "ibkr_take_profit_order_id")
    private Integer ibkrTakeProfitOrderId;

    @Column(name = "ibkr_stop_loss_order_id")
    private Integer ibkrStopLossOrderId;

    @Column(name = "ibkr_perm_id")
    private Long ibkrPermId;

    @Column(name = "order_status", length = 50)
    private String orderStatus = Status.PENDING;

    /**
     * Last raw status string from IBKR (e.g. "Submitted", "Filled", "ApiCancelled").
     * Distinct from {@code orderStatus} so we keep a verbatim copy for audit.
     */
    @Column(name = "ibkr_status_raw", length = 50)
    private String ibkrStatusRaw;

    @Column(name = "filled_qty", precision = 15, scale = 4, nullable = false)
    private BigDecimal filledQty = BigDecimal.ZERO;

    @Column(name = "remaining_qty", precision = 15, scale = 4)
    private BigDecimal remainingQty;

    @Column(name = "avg_fill_price", precision = 15, scale = 4)
    private BigDecimal avgFillPrice;

    @Column(name = "last_fill_price", precision = 15, scale = 4)
    private BigDecimal lastFillPrice;

    @Column(name = "last_ibkr_update")
    private LocalDateTime lastIbkrUpdate;

    // For idempotency - prevents duplicate orders
    @Column(name = "client_order_id", unique = true, length = 100)
    private String clientOrderId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
