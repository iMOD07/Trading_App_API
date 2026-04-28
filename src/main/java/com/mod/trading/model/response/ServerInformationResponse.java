package com.mod.trading.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ServerInformationResponse {
    private String serverName;
    private String serverHost;
    private String mode;
}
