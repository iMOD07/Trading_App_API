package com.mod.trading.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;


import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean default false")
    private boolean active = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(name = "trade_amount")
    private double tradeAmount = 500;

    @Column(name = "range_value")
    private double rangeValue = 0.01;

    @Column(name = "profit_percent")
    private double profitPercent = 6;

    @Column(name = "ibkr_account", nullable = false)
    private String IbkrAccount;

    @Column(name = "ibkr_host", nullable = false)
    private String ibkrHost ;

    @Column(name = "ibkr_port", nullable = false)
    private int ibkrPort ;

    @Column(name = "ibkr_client_id", nullable = false)
    private int ibkrClientId ;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<TradeOrder> orders;

}
