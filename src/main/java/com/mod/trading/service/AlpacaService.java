package com.mod.trading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mod.trading.entity.TradeOrder;
import com.mod.trading.entity.User;
import com.mod.trading.model.TradeRequest;
import com.mod.trading.repository.TradeOrderRepository;
import com.mod.trading.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlpacaService {

    private final TradeOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Send order - Responds to settings from the user
    public TradeOrder placeOrder(TradeRequest request, String username) throws Exception {

        User user = getUser(username);

        // Settings from User
        double tradeAmount  = user.getTradeAmount();
        double range        = user.getRangeValue();
        double profitPct    = user.getProfitPercent();

        // الحسابات
        // Accounts Ready
        String symbol     = request.getSymbol().toUpperCase();
        int qty           = (int) Math.floor(tradeAmount / request.getEntryPrice());
        double stopPrice  = round(request.getEntryPrice() + range);
        double limitPrice = round(request.getEntryPrice() + (range * 2));
        double takeProfit = round(request.getEntryPrice() * (1 + profitPct / 100));
        double stopLoss   = request.getStopLoss();

        log.info("📊 [{}] symbol={} qty={} stop={} limit={} tp={} sl={}",
                username, symbol, qty, stopPrice, limitPrice, takeProfit, stopLoss);

        Map<String, Object> body = new HashMap<>();
        body.put("symbol",        symbol);
        body.put("qty",           String.valueOf(qty));
        body.put("side",          "buy");
        body.put("type",          "stop_limit");
        body.put("stop_price",    String.valueOf(stopPrice));
        body.put("limit_price",   String.valueOf(limitPrice));
        body.put("time_in_force", "gtc");
        body.put("order_class",   "bracket");

        Map<String, String> tp = new HashMap<>();
        tp.put("limit_price", String.valueOf(takeProfit));
        body.put("take_profit", tp);

        Map<String, String> sl = new HashMap<>();
        sl.put("stop_price", String.valueOf(stopLoss));
        body.put("stop_loss", sl);

        String jsonBody = objectMapper.writeValueAsString(body);

        // Send using user keys
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(user.getAlpacaBaseUrl() + "/v2/orders"))
                .header("APCA-API-KEY-ID", user.getAlpacaApiKey())
                .header("APCA-API-SECRET-KEY", user.getAlpacaApiSecret())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        log.info("Reply: {}", responseBody);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Alpaca error [" + response.statusCode() + "]: " + responseBody);
        }

        Map<String, Object> alpacaResponse = objectMapper.readValue(responseBody, Map.class);

        // Save in the database
        TradeOrder order = new TradeOrder();
        order.setUser(user);
        order.setSymbol(symbol);
        order.setQty(qty);
        order.setEntryPrice(request.getEntryPrice());
        order.setTradeAmount(tradeAmount);
        order.setProfitPercent(profitPct);
        order.setStopPrice(stopPrice);
        order.setLimitPrice(limitPrice);
        order.setTakeProfit(takeProfit);
        order.setStopLoss(stopLoss);
        order.setAlpacaOrderId(String.valueOf(alpacaResponse.get("id")));
        order.setOrderStatus(String.valueOf(alpacaResponse.getOrDefault("status", "unknown")));
        order.setRawResponse(responseBody);

        return orderRepository.save(order);
    }

    // Account Information
    public Map<String, Object> getAccount(String username) throws Exception {
        User user = getUser(username);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(user.getAlpacaBaseUrl() + "/v2/account"))
                .header("APCA-API-KEY-ID", user.getAlpacaApiKey())
                .header("APCA-API-SECRET-KEY", user.getAlpacaApiSecret())
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), Map.class);
    }

    // Open orders from Alpaca
    public Object getAlpacaOrders(String username) throws Exception {
        User user = getUser(username);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(user.getAlpacaBaseUrl() + "/v2/orders?status=open"))
                .header("APCA-API-KEY-ID", user.getAlpacaApiKey())
                .header("APCA-API-SECRET-KEY", user.getAlpacaApiSecret())
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), Object.class);
    }

    // Deal log
    public List<TradeOrder> getAllOrders(String username) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(getUser(username).getId());
    }

    public List<TradeOrder> getOrdersBySymbol(String username, String symbol) {
        return orderRepository.findByUserIdAndSymbolOrderByCreatedAtDesc(getUser(username).getId(), symbol.toUpperCase());
    }

    public List<TradeOrder> getLastOrders(String username) {
        return orderRepository.findTop10ByUserIdOrderByCreatedAtDesc(getUser(username).getId());
    }

    // User settings
    public Map<String, Object> getSettings(String username) {
        User user = getUser(username);
        return Map.of(
                "tradeAmount",   user.getTradeAmount(),
                "rangeValue",    user.getRangeValue(),
                "profitPercent", user.getProfitPercent()
        );
    }

    public User updateSettings(String username, double tradeAmount, double rangeValue, double profitPercent) {
        User user = getUser(username);
        user.setTradeAmount(tradeAmount);
        user.setRangeValue(rangeValue);
        user.setProfitPercent(profitPercent);
        return userRepository.save(user);
    }

    // Helper
    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}