package com.mod.trading.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /**
     * Client-generated UUID for idempotent retries (A3).
     * If the network drops after the server placed the order but before
     * the client received the response, the client may retry safely with
     * the SAME clientOrderId; the server will return the original order
     * without double-submitting to IBKR.
     *
     * Optional for backward compatibility; if absent, the server generates one
     * (and idempotency is lost — clients SHOULD send it).
     */
    @Size(max = 100)
    private String clientOrderId;
}
