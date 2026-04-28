package com.mod.trading.controller;

import com.mod.trading.entity.TradeOrder;
import com.mod.trading.model.SettingsRequest;
import com.mod.trading.model.TradeRequest;
import com.mod.trading.service.IbkrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TradeController {

    private final IbkrService ibkrService;

    // ===== Orders =====

    @PostMapping("/api/trade/order")
    public ResponseEntity<TradeOrder> placeOrder(
            @Valid @RequestBody TradeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.placeOrder(request, userDetails.getUsername()));
    }

    @DeleteMapping("/api/trade/order/{id}")
    public ResponseEntity<Map<String, String>> cancelOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        ibkrService.cancelOrder(id, userDetails.getUsername());
        return ResponseEntity.ok(Map.of("message", "Order cancelled"));
    }

    @GetMapping("/api/trade/orders")
    public ResponseEntity<List<TradeOrder>> getAllOrders(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getAllOrders(userDetails.getUsername()));
    }

    @GetMapping("/api/trade/orders/last")
    public ResponseEntity<List<TradeOrder>> getLastOrders(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getLastOrders(userDetails.getUsername()));
    }

    @GetMapping("/api/trade/orders/symbol/{symbol}")
    public ResponseEntity<List<TradeOrder>> getOrdersBySymbol(
            @PathVariable String symbol,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getOrdersBySymbol(userDetails.getUsername(), symbol));
    }

    @GetMapping("/api/trade/connection")
    public ResponseEntity<Map<String, Object>> testConnection(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.testConnection(userDetails.getUsername()));
    }

    // ===== Settings =====

    @GetMapping("/api/settings")
    public ResponseEntity<Map<String, Object>> getSettings(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getSettings(userDetails.getUsername()));
    }

    @PostMapping("/api/settings")
    public ResponseEntity<Map<String, String>> updateSettings(
            @Valid @RequestBody SettingsRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        ibkrService.updateSettings(
                userDetails.getUsername(),
                request.getTradeAmount(),
                request.getRangeValue(),
                request.getProfitPercent()
        );
        return ResponseEntity.ok(Map.of("message", "Settings updated"));
    }
}
