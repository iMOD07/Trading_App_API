package com.mod.trading.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ServerInformationRequest {

    @NotBlank
    private String serverName;

    @NotBlank
    private String serverHost;

    @NotBlank // "LIVE" or "TEST"
    private String mode;
}
