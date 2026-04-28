package com.mod.trading.model;

import com.mod.trading.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private Role role;
    private boolean ibkrConfigured;  // Hint for Flutter to show config screen
}
