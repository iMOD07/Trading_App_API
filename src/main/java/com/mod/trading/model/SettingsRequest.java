package com.mod.trading.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SettingsRequest {

    @NotNull
    @DecimalMin(value = "1.00", message = "Trade amount must be at least 1.00")
    private BigDecimal tradeAmount;

    @NotNull
    @DecimalMin(value = "0.0001", message = "Range must be positive")
    private BigDecimal rangeValue;

    @NotNull
    @DecimalMin(value = "0.01", message = "Profit % must be positive")
    private BigDecimal profitPercent;
}
