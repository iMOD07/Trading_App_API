package com.mod.trading.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeRequest {

    @NotBlank(message = "Symbol required")
    @Pattern(regexp = "^[A-Z]{1,10}$", message = "Symbol must be 1-10 uppercase letters")
    private String symbol;

    @NotNull
    @DecimalMin(value = "0.01", message = "Entry price must be > 0")
    private BigDecimal entryPrice;

    @NotNull
    @DecimalMin(value = "0.01", message = "Stop loss must be > 0")
    private BigDecimal stopLoss;
}
