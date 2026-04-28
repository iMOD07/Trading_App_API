package com.mod.trading.ibkr;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.DefaultEWrapper;
import com.ib.client.EClientSocket;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.mod.trading.config.TradeWebSocketHandler;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Receives all asynchronous callbacks from IB Gateway.
 *
 * The TWS API is event-driven: when you call placeOrder(), nothing returns
 * synchronously. Status updates, fills, and errors arrive later via the
 * callback methods below. We bridge that model to CompletableFutures so the
 * service layer can use familiar await/then semantics.
 *
 * Only the methods we actually use are documented; all others simply log.
 */
@Slf4j
@Component
public class IbkrEventWrapper extends com.ib.client.DefaultEWrapper {

    @Setter
    private EClientSocket clientSocket;

    private final TradeWebSocketHandler webSocketHandler;

    /** Provided by IBKR after connect; we use it to allocate orderIds. */
    private final AtomicInteger nextOrderId = new AtomicInteger(-1);

    /** orderId -> future that completes when the order is acknowledged */
    private final Map<Integer, CompletableFuture<OrderAck>> pendingOrders = new ConcurrentHashMap<>();

    /** reqId -> future for account summary requests */
    private final Map<Integer, CompletableFuture<Map<String, String>>> pendingAccountReqs = new ConcurrentHashMap<>();

    /** reqId -> accumulating account summary data */
    private final Map<Integer, Map<String, String>> accountAccumulator = new ConcurrentHashMap<>();

    /** Final status keywords from TWS - if we hear these, the order is at terminal/working state */
    private static final Set<String> WORKING_STATUSES = Set.of(
            "PreSubmitted", "Submitted", "Filled", "ApiCancelled", "Cancelled");

    public IbkrEventWrapper(@Lazy TradeWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    // ============================================================
    // Order ID management - IBKR assigns these centrally
    // ============================================================

    @Override
    public void nextValidId(int orderId) {
        log.info("IBKR nextValidId received: {}", orderId);
        nextOrderId.set(orderId);
    }

    public int allocateOrderId() {
        int id = nextOrderId.getAndIncrement();
        if (id < 0) {
            throw new IllegalStateException(
                    "nextValidId not received from IBKR yet. Wait for connection.");
        }
        return id;
    }

    public boolean isReady() {
        return nextOrderId.get() >= 0;
    }

    // ============================================================
    // Order placement bridge: register expectation BEFORE calling placeOrder
    // ============================================================

    public CompletableFuture<OrderAck> registerPendingOrder(int orderId) {
        CompletableFuture<OrderAck> future = new CompletableFuture<>();
        pendingOrders.put(orderId, future);
        return future;
    }

    @Override
    public void orderStatus(int orderId, String status, Decimal filled, Decimal remaining,
                            double avgFillPrice, long permId, int parentId, double lastFillPrice,
                            int clientId, String whyHeld, double mktCapPrice) {
        log.info("orderStatus: id={} status={} filled={} avgPrice={}",
                orderId, status, filled, avgFillPrice);

        CompletableFuture<OrderAck> future = pendingOrders.get(orderId);
        if (future != null && !future.isDone() && WORKING_STATUSES.contains(status)) {
            future.complete(new OrderAck(orderId, status, BigDecimal.valueOf(avgFillPrice), permId));
            // We don't remove it - subsequent updates (fills, cancellations) keep flowing
            // and we forward them to the WebSocket below.
        }

        // Forward status updates via WebSocket so the UI updates live
        webSocketHandler.broadcast("ORDER_STATUS", Map.of(
                "orderId", orderId,
                "status", status,
                "filled", filled.toString(),
                "remaining", remaining.toString(),
                "avgFillPrice", avgFillPrice
        ));
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState state) {
        log.debug("openOrder: id={} symbol={} state={}", orderId, contract.symbol(), state.getStatus());
    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        log.info("execDetails: orderId={} symbol={} qty={} price={}",
                execution.orderId(), contract.symbol(), execution.shares(), execution.price());

        webSocketHandler.broadcast("EXECUTION", Map.of(
                "orderId", execution.orderId(),
                "symbol", contract.symbol(),
                "shares", execution.shares().toString(),
                "price", execution.price(),
                "time", execution.time()
        ));
    }

    // ============================================================
    // Account summary
    // ============================================================

    public CompletableFuture<Map<String, String>> registerAccountSummary(int reqId) {
        CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
        pendingAccountReqs.put(reqId, future);
        accountAccumulator.put(reqId, new ConcurrentHashMap<>());
        return future;
    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
        Map<String, String> data = accountAccumulator.get(reqId);
        if (data != null) {
            data.put(tag, value + (currency != null && !currency.isEmpty() ? " " + currency : ""));
        }
    }

    @Override
    public void accountSummaryEnd(int reqId) {
        Map<String, String> data = accountAccumulator.remove(reqId);
        CompletableFuture<Map<String, String>> future = pendingAccountReqs.remove(reqId);
        if (future != null && data != null) {
            future.complete(data);
        }
    }

    // ============================================================
    // Errors
    // ============================================================

    @Override
    public void error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        // IBKR uses error() also for informational messages (codes 2104-2158)
        if (errorCode >= 2100 && errorCode < 2200) {
            log.debug("IBKR info [{}]: {}", errorCode, errorMsg);
            return;
        }

        log.warn("IBKR error: id={} code={} msg={}", id, errorCode, errorMsg);

        // Fail any pending future tied to this id (orderId or reqId)
        CompletableFuture<OrderAck> orderFuture = pendingOrders.remove(id);
        if (orderFuture != null && !orderFuture.isDone()) {
            orderFuture.completeExceptionally(new IbkrException(errorCode, errorMsg));
        }
        CompletableFuture<Map<String, String>> acctFuture = pendingAccountReqs.remove(id);
        if (acctFuture != null && !acctFuture.isDone()) {
            acctFuture.completeExceptionally(new IbkrException(errorCode, errorMsg));
        }
    }

    @Override
    public void error(Exception e) {
        log.error("IBKR socket exception", e);
    }

    @Override
    public void connectionClosed() {
        log.warn("IBKR connection closed");
        // Fail all pending operations
        pendingOrders.values().forEach(f -> f.completeExceptionally(
                new IbkrException(-1, "Connection closed")));
        pendingOrders.clear();
        pendingAccountReqs.values().forEach(f -> f.completeExceptionally(
                new IbkrException(-1, "Connection closed")));
        pendingAccountReqs.clear();
    }

    /**
     * Lightweight DTO returned to service layer when an order is acknowledged.
     */
    public record OrderAck(int orderId, String status, BigDecimal avgFillPrice, long permId) {}
}
