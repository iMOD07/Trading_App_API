package com.mod.trading.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username must be alphanumeric with . _ - allowed")
    private String username;

    @NotBlank
    @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
    private String password;

    /**
     * Optional IBKR sub-account (e.g. "U1234567" for live, "DU..." for paper).
     * Required only if the operator's IB Gateway logs into a Financial Advisor
     * master account with multiple sub-accounts.
     */
    @Pattern(regexp = "^(U|DU)\\d{6,10}$|^$", message = "IBKR account must look like U1234567 or DU1234567")
    private String ibkrAccountId;
}
