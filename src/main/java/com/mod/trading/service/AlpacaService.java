package com.mod.trading.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mod.trading.config.TradeWebSocketHandler;
import com.mod.trading.entity.TradeOrder;
import com.mod.trading.entity.User;
import com.mod.trading.exception.AlpacaApiException;
import com.mod.trading.exception.InvalidTradeRequestException;
import com.mod.trading.exception.TradingDisabledException;
import com.mod.trading.exception.UserNotFoundException;
import com.mod.trading.model.TradeRequest;
import com.mod.trading.repository.TradeOrderRepository;
import com.mod.trading.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlpacaService {

    private final TradeOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TradeWebSocketHandler webSocketHandler;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};

    /**
     * Places a bracket order with the user's configured settings.
     * Uses Alpaca's client_order_id for idempotency: a network retry will not
     * create a duplicate position.
     */
    @Transactional
    @CircuitBreaker(name = "alpaca")
    @Retry(name = "alpaca")
    public TradeOrder placeOrder(TradeRequest request, String username) {
        User user = getUser(username);

        MDC.put("userId", String.valueOf(user.getId()));
        MDC.put("symbol", request.getSymbol());
        try {
            return doPlaceOrder(request, user);
        } finally {
            MDC.clear();
        }
    }

    private TradeOrder doPlaceOrder(TradeRequest request, User user) {
        if (!user.isTradingEnabled()) {
            throw new TradingDisabledException("Trading disabled for this account");
        }

        // Daily loss limit check
        if (user.getDailyLossLimit() != null && user.getDailyLossLimit().signum() > 0) {
            BigDecimal todayLossPotential = orderRepository.sumPotentialLossesToday(
                    user.getId(), LocalDate.now().atStartOfDay());
            if (todayLossPotential.compareTo(user.getDailyLossLimit()) >= 0) {
                throw new TradingDisabledException(
                        "Daily loss limit reached: " + todayLossPotential + " / " + user.getDailyLossLimit());
            }
        }

        String symbol = request.getSymbol().toUpperCase();
        BigDecimal entryPrice = request.getEntryPrice();
        BigDecimal stopLoss = request.getStopLoss();
        BigDecimal tradeAmount = user.getTradeAmount();
        BigDecimal range = user.getRangeValue();
        BigDecimal profitPct = user.getProfitPercent();

        // Validation: stop loss must be below entry price (for buy orders)
        if (stopLoss.compareTo(entryPrice) >= 0) {
            throw new InvalidTradeRequestException(
                    "Stop loss (" + stopLoss + ") must be below entry price (" + entryPrice + ")");
        }

        // Computations using BigDecimal
        int qty = tradeAmount.divide(entryPrice, 0, RoundingMode.DOWN).intValue();
        if (qty <= 0) {
            throw new InvalidTradeRequestException(
                    "Trade amount " + tradeAmount + " insufficient for " + symbol + " at " + entryPrice);
        }

        BigDecimal stopPrice = entryPrice.add(range).setScale(2, RoundingMode.HALF_UP);
        BigDecimal limitPrice = entryPrice.add(range.multiply(TWO)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal takeProfit = entryPrice
                .multiply(BigDecimal.ONE.add(profitPct.divide(HUNDRED, 8, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        // Sanity check: take profit must be above stop loss
        if (takeProfit.compareTo(stopLoss) <= 0) {
            throw new InvalidTradeRequestException(
                    "Take profit (" + takeProfit + ") must be above stop loss (" + stopLoss + ")");
        }

        log.info("Placing order: symbol={} qty={} stop={} limit={} tp={} sl={}",
                symbol, qty, stopPrice, limitPrice, takeProfit, stopLoss);

        // Idempotency key for Alpaca
        String clientOrderId = "TB-" + UUID.randomUUID();

        Map<String, Object> body = new HashMap<>();
        body.put("symbol", symbol);
        body.put("qty", String.valueOf(qty));
        body.put("side", "buy");
        body.put("type", "stop_limit");
        body.put("stop_price", stopPrice.toPlainString());
        body.put("limit_price", limitPrice.toPlainString());
        body.put("time_in_force", "gtc");
        body.put("order_class", "bracket");
        body.put("client_order_id", clientOrderId);
        body.put("take_profit", Map.of("limit_price", takeProfit.toPlainString()));
        body.put("stop_loss", Map.of("stop_price", stopLoss.toPlainString()));

        Map<String, Object> alpacaResponse;
        String responseBody;
        try {
            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(user.getAlpacaBaseUrl() + "/v2/orders"))
                    .header("APCA-API-KEY-ID", user.getAlpacaApiKey())
                    .header("APCA-API-SECRET-KEY", user.getAlpacaApiSecret())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            responseBody = response.body();

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Alpaca rejected order: status={}, body={}", response.statusCode(), responseBody);
                throw new AlpacaApiException(response.statusCode(), responseBody);
            }
            alpacaResponse = objectMapper.readValue(responseBody, MAP_TYPE);
        } catch (AlpacaApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Alpaca communication failure for clientOrderId={}", clientOrderId, e);
            throw new AlpacaApiException(0, "Alpaca communication error: " + e.getMessage());
        }

        TradeOrder order = new TradeOrder();
        order.setUser(user);
        order.setClientOrderId(clientOrderId);
        order.setSymbol(symbol);
        order.setQty(qty);
        order.setEntryPrice(entryPrice);
        order.setTradeAmount(tradeAmount);
        order.setProfitPercent(profitPct);
        order.setStopPrice(stopPrice);
        order.setLimitPrice(limitPrice);
        order.setTakeProfit(takeProfit);
        order.setStopLoss(stopLoss);
        order.setAlpacaOrderId(String.valueOf(alpacaResponse.get("id")));
        order.setOrderStatus(String.valueOf(alpacaResponse.getOrDefault("status", "unknown")));
        order.setRawResponse(responseBody);

        TradeOrder saved = orderRepository.save(order);

        // Notify connected WS clients for this user
        webSocketHandler.sendToUser(user.getUsername(), "ORDER_PLACED", Map.of(
                "id", saved.getId(),
                "symbol", saved.getSymbol(),
                "qty", saved.getQty(),
                "entryPrice", saved.getEntryPrice(),
                "alpacaOrderId", saved.getAlpacaOrderId(),
                "status", saved.getOrderStatus()
        ));

        return saved;
    }

    @CircuitBreaker(name = "alpaca")
    @Retry(name = "alpaca")
    public Map<String, Object> getAccount(String username) {
        User user = getUser(username);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(user.getAlpacaBaseUrl() + "/v2/account"))
                    .header("APCA-API-KEY-ID", user.getAlpacaApiKey())
                    .header("APCA-API-SECRET-KEY", user.getAlpacaApiSecret())
                    .timeout(Duration.ofSeconds(20))
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new AlpacaApiException(response.statusCode(), response.body());
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (AlpacaApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AlpacaApiException(0, e.getMessage());
        }
    }

    @CircuitBreaker(name = "alpaca")
    @Retry(name = "alpaca")
    public List<Map<String, Object>> getAlpacaOrders(String username) {
        User user = getUser(username);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(user.getAlpacaBaseUrl() + "/v2/orders?status=open"))
                    .header("APCA-API-KEY-ID", user.getAlpacaApiKey())
                    .header("APCA-API-SECRET-KEY", user.getAlpacaApiSecret())
                    .timeout(Duration.ofSeconds(20))
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new AlpacaApiException(response.statusCode(), response.body());
            }
            return objectMapper.readValue(response.body(), LIST_MAP_TYPE);
        } catch (AlpacaApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AlpacaApiException(0, e.getMessage());
        }
    }

    public List<TradeOrder> getOrders(String username, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(getUser(username).getId(), pageable)
                .getContent();
    }

    public List<TradeOrder> getOrdersBySymbol(String username, String symbol, Pageable pageable) {
        return orderRepository.findByUserIdAndSymbolOrderByCreatedAtDesc(
                getUser(username).getId(), symbol.toUpperCase(), pageable).getContent();
    }

    public List<TradeOrder> getLastOrders(String username) {
        return orderRepository.findTop10ByUserIdOrderByCreatedAtDesc(getUser(username).getId());
    }

    public Map<String, Object> getSettings(String username) {
        User user = getUser(username);
        Map<String, Object> settings = new HashMap<>();
        settings.put("tradeAmount", user.getTradeAmount());
        settings.put("rangeValue", user.getRangeValue());
        settings.put("profitPercent", user.getProfitPercent());
        settings.put("dailyLossLimit", user.getDailyLossLimit());
        settings.put("tradingEnabled", user.isTradingEnabled());
        return settings;
    }

    @Transactional
    public User updateSettings(String username, BigDecimal tradeAmount, BigDecimal rangeValue,
                               BigDecimal profitPercent, BigDecimal dailyLossLimit) {
        User user = getUser(username);
        user.setTradeAmount(tradeAmount);
        user.setRangeValue(rangeValue);
        user.setProfitPercent(profitPercent);
        if (dailyLossLimit != null) {
            user.setDailyLossLimit(dailyLossLimit);
        }
        return userRepository.save(user);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }
}
