package com.mod.trading.model;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Used when an authenticated user updates their own profile.
 * Username is NOT in this DTO - it is always taken from the authenticated principal.
 * All fields are optional (partial update).
 */
@Data
public class UpdateProfileRequest {

    @Size(min = 8, max = 100)
    private String password;

    private String alpacaApiKey;
    private String alpacaApiSecret;

    @Pattern(regexp = "^https://(paper-api|api)\\.alpaca\\.markets$",
             message = "Base URL must be a valid Alpaca endpoint")
    private String alpacaBaseUrl;
}
