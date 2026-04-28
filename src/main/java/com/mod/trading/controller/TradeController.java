package com.mod.trading.controller;

import com.mod.trading.entity.TradeOrder;
import com.mod.trading.model.request.TradeRequest;
import com.mod.trading.service.IbkrService;
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
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradeController {

    private final IbkrService ibkrService;

    // POST /api/trade/order
    @PostMapping("/order")
    public ResponseEntity<?> placeOrder(@Valid @RequestBody TradeRequest request,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        try {
            TradeOrder order = ibkrService.placeOrder(request, userDetails.getUsername());
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            log.error("Error placing order: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trade/account
    @GetMapping("/account")
    public ResponseEntity<?> getAccount(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(ibkrService.getAccount(userDetails.getUsername()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trade/status - حالة الاتصال بـ Gateway
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getConnectionStatus(userDetails.getUsername()));
    }

    // DELETE /api/trade/orders/{ibkrOrderId}
    @DeleteMapping("/orders/{ibkrOrderId}")
    public ResponseEntity<?> cancelOrder(@PathVariable String ibkrOrderId,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(ibkrService.cancelOrder(ibkrOrderId, userDetails.getUsername()));
        } catch (Exception e) {
            log.error("Cancel error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trade/orders/open
    @GetMapping("/orders/open")
    public ResponseEntity<?> getOpenOrders(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(ibkrService.getOpenOrders(userDetails.getUsername()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trade/orders
    @GetMapping("/orders")
    public ResponseEntity<List<TradeOrder>> getAllOrders(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getAllOrders(userDetails.getUsername()));
    }

    // GET /api/trade/orders/last
    @GetMapping("/orders/last")
    public ResponseEntity<List<TradeOrder>> getLastOrders(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getLastOrders(userDetails.getUsername()));
    }

    // GET /api/trade/orders/symbol/{symbol}
    @GetMapping("/orders/symbol/{symbol}")
    public ResponseEntity<List<TradeOrder>> getOrdersBySymbol(@PathVariable String symbol,
                                                              @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ibkrService.getOrdersBySymbol(userDetails.getUsername(), symbol));
    }
}
