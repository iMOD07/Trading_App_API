package com.mod.trading.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String ibkrAccount;

    @NotBlank
    private String ibkrHost;

    @NotNull
    private Integer ibkrPort;

    @NotNull
    private Integer ibkrClientId;
}
