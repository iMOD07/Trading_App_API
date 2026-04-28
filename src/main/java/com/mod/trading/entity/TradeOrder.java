package com.mod.trading.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "trade_orders", indexes = {
        @Index(name = "idx_orders_user_created", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_orders_user_symbol", columnList = "user_id, symbol"),
        @Index(name = "idx_orders_alpaca_id", columnList = "alpaca_order_id"),
        @Index(name = "idx_orders_client_order_id", columnList = "client_order_id", unique = true)
})
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    /**
     * Idempotency key sent to Alpaca to prevent duplicate orders on retry.
     */
    @Column(name = "client_order_id", nullable = false, unique = true, length = 64)
    private String clientOrderId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private int qty;

    @Column(name = "entry_price", precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "trade_amount", precision = 19, scale = 4)
    private BigDecimal tradeAmount;

    @Column(name = "profit_percent", precision = 19, scale = 4)
    private BigDecimal profitPercent;

    @Column(name = "stop_price", precision = 19, scale = 4)
    private BigDecimal stopPrice;

    @Column(name = "limit_price", precision = 19, scale = 4)
    private BigDecimal limitPrice;

    @Column(name = "take_profit", precision = 19, scale = 4)
    private BigDecimal takeProfit;

    @Column(name = "stop_loss", precision = 19, scale = 4)
    private BigDecimal stopLoss;

    @Column(name = "alpaca_order_id", length = 100)
    private String alpacaOrderId;

    @Column(name = "order_status", length = 50)
    private String orderStatus;

    @JsonIgnore
    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
