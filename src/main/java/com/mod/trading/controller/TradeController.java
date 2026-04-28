package com.mod.trading.controller;

import com.mod.trading.entity.TradeOrder;
import com.mod.trading.model.SettingsRequest;
import com.mod.trading.model.TradeRequest;
import com.mod.trading.service.IbkrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @PostMapping("/api/trade/order")
    public ResponseEntity<TradeOrder> placeOrder(@Valid @RequestBody TradeRequest request,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.placeOrder(request, userDetails.getUsername()));
    }

    @PostMapping("/api/trade/orders/{id}/cancel")
    public ResponseEntity<Map<String, String>> cancelOrder(@PathVariable Long id,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        ibkrService.cancelOrder(id, userDetails.getUsername());
        return ResponseEntity.ok(Map.of("message", "Cancel request sent"));
    }

    @GetMapping("/api/trade/account")
    public ResponseEntity<?> getAccount(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getAccountSummary(userDetails.getUsername()));
    }

    @GetMapping("/api/trade/orders")
    public ResponseEntity<List<TradeOrder>> getAllOrders(@AuthenticationPrincipal UserDetails userDetails,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return ResponseEntity.ok(ibkrService.getOrders(userDetails.getUsername(), pageable));
    }

    @GetMapping("/api/trade/orders/last")
    public ResponseEntity<List<TradeOrder>> getLastOrders(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getLastOrders(userDetails.getUsername()));
    }

    @GetMapping("/api/trade/orders/symbol/{symbol}")
    public ResponseEntity<List<TradeOrder>> getOrdersBySymbol(@PathVariable String symbol,
                                                              @AuthenticationPrincipal UserDetails userDetails,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return ResponseEntity.ok(ibkrService.getOrdersBySymbol(userDetails.getUsername(), symbol, pageable));
    }

    @GetMapping("/api/trade/connection")
    public ResponseEntity<Map<String, Object>> connectionStatus() {
        return ResponseEntity.ok(Map.of("ibkrConnected", ibkrService.isConnected()));
    }

    @GetMapping("/api/settings")
    public ResponseEntity<?> getSettings(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getSettings(userDetails.getUsername()));
    }

    @PostMapping("/api/settings")
    public ResponseEntity<?> updateSettings(@Valid @RequestBody SettingsRequest request,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        ibkrService.updateSettings(
                userDetails.getUsername(),
                request.getTradeAmount(),
                request.getRangeValue(),
                request.getProfitPercent(),
                request.getDailyLossLimit()
        );
        return ResponseEntity.ok(Map.of("message", "Settings updated successfully"));
    }
}
