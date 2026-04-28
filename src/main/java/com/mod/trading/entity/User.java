package com.mod.trading.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    // ===== IBKR Connection Settings =====
    // Each user has their own VPS running IB Gateway
    @Column(name = "ibkr_host")
    private String ibkrHost;                  // "45.32.123.45" or VPN address

    @Column(name = "ibkr_port")
    private Integer ibkrPort = 4002;          // 4002=Paper, 4001=Live

    @Column(name = "ibkr_client_id")
    private Integer ibkrClientId = 1;         // Inside the Gateway

    @Column(name = "ibkr_account_id", length = 20)
    private String ibkrAccountId;             // U1234567 (Live) or DU1234567 (Paper)

    @Column(name = "ibkr_paper_trading", nullable = false)
    private boolean ibkrPaperTrading = true;  // Safety default

    // ===== Trading Settings =====
    @Column(name = "trade_amount", precision = 15, scale = 2)
    private BigDecimal tradeAmount = new BigDecimal("500.00");

    @Column(name = "range_value", precision = 10, scale = 4)
    private BigDecimal rangeValue = new BigDecimal("0.0100");

    @Column(name = "profit_percent", precision = 5, scale = 2)
    private BigDecimal profitPercent = new BigDecimal("6.00");

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TradeOrder> orders;

    /**
     * Check if user has fully configured IBKR connection.
     */
    public boolean isIbkrConfigured() {
        return ibkrHost != null && !ibkrHost.isBlank()
                && ibkrPort != null
                && ibkrAccountId != null && !ibkrAccountId.isBlank();
    }
}
