package com.mod.trading.controller;

import com.mod.trading.entity.TradeOrder;
import com.mod.trading.model.SettingsRequest;
import com.mod.trading.model.TradeRequest;
import com.mod.trading.service.AlpacaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TradeController {

    private final AlpacaService alpacaService;

    // POST /api/trade/order
    @PostMapping("/api/trade/order")
    public ResponseEntity<?> placeOrder(@Valid @RequestBody TradeRequest request,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        try {
            TradeOrder order = alpacaService.placeOrder(request, userDetails.getUsername());
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trade/account
    @GetMapping("/api/trade/account")
    public ResponseEntity<?> getAccount(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(alpacaService.getAccount(userDetails.getUsername()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // DELETE /api/trade/orders/{alpacaOrderId}
    @DeleteMapping("/api/trade/orders/{alpacaOrderId}")
    public ResponseEntity<?> cancelOrder(@PathVariable String alpacaOrderId,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(alpacaService.cancelOrder(alpacaOrderId, userDetails.getUsername()));
        } catch (Exception e) {
            log.error("Cancel error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trade/orders/alpaca
    @GetMapping("/api/trade/orders/alpaca")
    public ResponseEntity<?> getAlpacaOrders(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(alpacaService.getAlpacaOrders(userDetails.getUsername()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trade/orders
    @GetMapping("/api/trade/orders")
    public ResponseEntity<List<TradeOrder>> getAllOrders(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(alpacaService.getAllOrders(userDetails.getUsername()));
    }

    // GET /api/trade/orders/last
    @GetMapping("/api/trade/orders/last")
    public ResponseEntity<List<TradeOrder>> getLastOrders(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(alpacaService.getLastOrders(userDetails.getUsername()));
    }

    // GET /api/trade/orders/symbol/{symbol}
    @GetMapping("/api/trade/orders/symbol/{symbol}")
    public ResponseEntity<List<TradeOrder>> getOrdersBySymbol(@PathVariable String symbol,
                                                              @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(alpacaService.getOrdersBySymbol(userDetails.getUsername(), symbol));
    }

    // GET /api/settings
    @GetMapping("/api/settings")
    public ResponseEntity<?> getSettings(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(alpacaService.getSettings(userDetails.getUsername()));
    }

    // POST /api/settings
    @PostMapping("/api/settings")
    public ResponseEntity<?> updateSettings(@Valid @RequestBody SettingsRequest request,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            alpacaService.updateSettings(
                    userDetails.getUsername(),
                    request.getTradeAmount(),
                    request.getRangeValue(),
                    request.getProfitPercent()
            );
            return ResponseEntity.ok(Map.of("message", "Settings updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}