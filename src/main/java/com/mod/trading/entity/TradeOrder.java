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

    // مرتبط بالمستخدم
    // User-related
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    // Stock Information
    @Column(nullable = false)
    private String symbol;

    private int qty;
    private double entryPrice;
    private double tradeAmount;
    private double profitPercent;

    // Price
    private double stopPrice;
    private double limitPrice;
    private double takeProfit;
    private double stopLoss;

    //Reply from Alpaca
    @Column(name = "alpaca_order_id")
    private String alpacaOrderId;

    @Column(name = "order_status")
    private String orderStatus;

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
}