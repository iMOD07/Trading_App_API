package com.mod.trading.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TradeRequest {

    @NotBlank(message = "Symbol required")
    private String symbol;

    @Positive(message = "Entry price must be positive")
    private double entryPrice;

    @Positive(message = "Stop loss must be positive")
    private double stopLoss;
}