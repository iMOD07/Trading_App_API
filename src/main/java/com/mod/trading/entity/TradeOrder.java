package com.mod.trading.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "trade_orders")
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    // Stock Info
    @Column(nullable = false)
    private String symbol;

    private int qty;
    private double entryPrice;
    private double tradeAmount;
    private double profitPercent;

    // Prices
    private double stopPrice;
    private double limitPrice;
    private double takeProfit;
    private double stopLoss;

    // IBKR
    @Column(name = "ibkr_order_id")
    private String ibkrOrderId;

    @Column(name = "order_status")
    private String orderStatus;

    // PENDING = محفوظ ينتظر Gateway يرجع online
    // لو PENDING هذا الـ flag يكون true
    @Column(name = "is_pending", nullable = false)
    private boolean pending = false;

    @Column(columnDefinition = "TEXT")
    private String rawResponse;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Helper - هل الأمر قابل للإلغاء؟
    @Transient
    public boolean isCancellable() {
        return ibkrOrderId != null &&
                (orderStatus != null &&
                        (orderStatus.equalsIgnoreCase("PreSubmitted") ||
                                orderStatus.equalsIgnoreCase("Submitted")));
    }
}
