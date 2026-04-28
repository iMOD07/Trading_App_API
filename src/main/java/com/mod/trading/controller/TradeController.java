package com.mod.trading.controller;

import com.mod.trading.entity.TradeOrder;
import com.mod.trading.model.SettingsRequest;
import com.mod.trading.model.TradeRequest;
import com.mod.trading.service.AlpacaService;
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

    private final AlpacaService alpacaService;

    @PostMapping("/api/trade/order")
    public ResponseEntity<TradeOrder> placeOrder(@Valid @RequestBody TradeRequest request,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(alpacaService.placeOrder(request, userDetails.getUsername()));
    }

    @GetMapping("/api/trade/account")
    public ResponseEntity<?> getAccount(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(alpacaService.getAccount(userDetails.getUsername()));
    }

    @GetMapping("/api/trade/orders/alpaca")
    public ResponseEntity<?> getAlpacaOrders(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(alpacaService.getAlpacaOrders(userDetails.getUsername()));
    }

    @GetMapping("/api/trade/orders")
    public ResponseEntity<List<TradeOrder>> getAllOrders(@AuthenticationPrincipal UserDetails userDetails,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return ResponseEntity.ok(alpacaService.getOrders(userDetails.getUsername(), pageable));
    }

    @GetMapping("/api/trade/orders/last")
    public ResponseEntity<List<TradeOrder>> getLastOrders(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(alpacaService.getLastOrders(userDetails.getUsername()));
    }

    @GetMapping("/api/trade/orders/symbol/{symbol}")
    public ResponseEntity<List<TradeOrder>> getOrdersBySymbol(@PathVariable String symbol,
                                                              @AuthenticationPrincipal UserDetails userDetails,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return ResponseEntity.ok(alpacaService.getOrdersBySymbol(userDetails.getUsername(), symbol, pageable));
    }

    @GetMapping("/api/settings")
    public ResponseEntity<?> getSettings(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(alpacaService.getSettings(userDetails.getUsername()));
    }

    @PostMapping("/api/settings")
    public ResponseEntity<?> updateSettings(@Valid @RequestBody SettingsRequest request,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        alpacaService.updateSettings(
                userDetails.getUsername(),
                request.getTradeAmount(),
                request.getRangeValue(),
                request.getProfitPercent(),
                request.getDailyLossLimit()
        );
        return ResponseEntity.ok(Map.of("message", "Settings updated successfully"));
    }
}
