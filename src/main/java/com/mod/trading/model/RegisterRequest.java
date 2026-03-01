package com.mod.trading.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String alpacaApiKey;

    @NotBlank
    private String alpacaApiSecret;

    private String alpacaBaseUrl = "https://paper-api.alpaca.markets";
}