package com.mod.trading.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mod.trading.util.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_username", columnList = "username", unique = true)
})
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

    // Alpaca credentials - encrypted at rest, never serialized to JSON
    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "alpaca_api_key", length = 512)
    private String alpacaApiKey;

    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "alpaca_api_secret", length = 512)
    private String alpacaApiSecret;

    @JsonIgnore
    @Column(name = "alpaca_base_url", length = 200)
    private String alpacaBaseUrl = "https://paper-api.alpaca.markets";

    // BigDecimal for money - never use double for monetary values
    @Column(name = "trade_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal tradeAmount = new BigDecimal("500.0000");

    @Column(name = "range_value", precision = 19, scale = 4, nullable = false)
    private BigDecimal rangeValue = new BigDecimal("0.0100");

    @Column(name = "profit_percent", precision = 19, scale = 4, nullable = false)
    private BigDecimal profitPercent = new BigDecimal("6.0000");

    // Risk controls
    @Column(name = "daily_loss_limit", precision = 19, scale = 4)
    private BigDecimal dailyLossLimit = new BigDecimal("1000.0000");

    @Column(name = "trading_enabled", nullable = false)
    private boolean tradingEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<TradeOrder> orders;
}
