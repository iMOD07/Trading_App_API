package com.mod.trading.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SettingsRequest {

    @NotNull
    @DecimalMin(value = "1.0", message = "Trade amount must be at least 1")
    @DecimalMax(value = "1000000.0", message = "Trade amount too large")
    private BigDecimal tradeAmount;

    @NotNull
    @DecimalMin(value = "0.0001", message = "Range must be > 0")
    @DecimalMax(value = "100.0", message = "Range too large")
    private BigDecimal rangeValue;

    @NotNull
    @DecimalMin(value = "0.01", message = "Profit percent must be > 0")
    @DecimalMax(value = "100.0", message = "Profit percent must be ≤ 100")
    private BigDecimal profitPercent;

    @DecimalMin(value = "0.0", message = "Daily loss limit must be ≥ 0")
    private BigDecimal dailyLossLimit;
}
