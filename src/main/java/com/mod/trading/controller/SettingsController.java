package com.mod.trading.controller;

import com.mod.trading.model.request.SettingsRequest;
import com.mod.trading.service.IbkrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final IbkrService ibkrService;

    // GET /api/settings
    @GetMapping
    public ResponseEntity<?> getSettings(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getSettings(userDetails.getUsername()));
    }

    // POST /api/settings
    @PostMapping
    public ResponseEntity<?> updateSettings(@Valid @RequestBody SettingsRequest request,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            ibkrService.updateSettings(
                    userDetails.getUsername(),
                    request.getTradeAmount(),
                    request.getRangeValue(),
                    request.getProfitPercent()
            );
            return ResponseEntity.ok(Map.of("message", "Settings updated successfully"));
        } catch (Exception error) {
            return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
        }
    }
}