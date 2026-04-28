package com.mod.trading.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Used by ADMIN to configure a user's IBKR connection.
 * Each user has a separate VPS running IB Gateway.
 */
@Data
public class IbkrConfigRequest {

    @NotBlank(message = "IBKR host (VPS IP) is required")
    private String ibkrHost;             // "45.32.123.45"

    @NotNull
    @Min(1024) @Max(65535)
    private Integer ibkrPort = 4002;     // 4002=Paper, 4001=Live

    @Min(0) @Max(999)
    private Integer ibkrClientId = 1;

    @NotBlank(message = "IBKR account ID is required")
    private String ibkrAccountId;        // U1234567 or DU1234567

    @NotNull
    private Boolean ibkrPaperTrading = true;
}
