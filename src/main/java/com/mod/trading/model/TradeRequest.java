package com.mod.trading.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeRequest {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Entry price is required")
    @DecimalMin(value = "0.01", message = "Entry price must be positive")
    private BigDecimal entryPrice;

    @NotNull(message = "Stop loss is required")
    @DecimalMin(value = "0.01", message = "Stop loss must be positive")
    private BigDecimal stopLoss;
}
