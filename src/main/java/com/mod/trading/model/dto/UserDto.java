package com.mod.trading.model.dto;

import com.mod.trading.entity.Role;
import com.mod.trading.entity.User;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class UserDto {
    private Long id;
    private String username;
    private boolean active;
    private Role role;
    private BigDecimal tradeAmount;
    private BigDecimal rangeValue;
    private BigDecimal profitPercent;
    private BigDecimal dailyLossLimit;
    private boolean tradingEnabled;
    private LocalDateTime createdAt;

    public static UserDto from(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .active(user.isActive())
                .role(user.getRole())
                .tradeAmount(user.getTradeAmount())
                .rangeValue(user.getRangeValue())
                .profitPercent(user.getProfitPercent())
                .dailyLossLimit(user.getDailyLossLimit())
                .tradingEnabled(user.isTradingEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
