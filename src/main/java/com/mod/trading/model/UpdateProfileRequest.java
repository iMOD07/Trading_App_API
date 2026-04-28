package com.mod.trading.model;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 8, max = 100)
    private String password;

    @Pattern(regexp = "^(U|DU)\\d{6,10}$|^$", message = "IBKR account must look like U1234567 or DU1234567")
    private String ibkrAccountId;
}
