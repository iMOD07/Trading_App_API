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
    private String orderStatus = "PENDING";

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
