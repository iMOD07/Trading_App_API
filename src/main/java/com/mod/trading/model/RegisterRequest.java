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

    @NotBlank
    private String alpacaApiKey;

    @NotBlank
    private String alpacaApiSecret;

    @Pattern(regexp = "^https://(paper-api|api)\\.alpaca\\.markets$",
             message = "Base URL must be a valid Alpaca endpoint")
    private String alpacaBaseUrl = "https://paper-api.alpaca.markets";
}
