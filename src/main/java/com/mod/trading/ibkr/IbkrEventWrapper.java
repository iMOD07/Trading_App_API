package com.mod.trading.ibkr;

import com.ib.client.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-user event wrapper. Each user's connection has its own wrapper
 * to handle async callbacks from IB Gateway.
 *
 * IBKR API is fully async - we use CompletableFuture to bridge callbacks
 * to a synchronous-looking API for the service layer.
 */
@Slf4j
public class IbkrEventWrapper extends DefaultEWrapper {

    private final String userTag;
    private final AtomicInteger nextOrderId = new AtomicInteger(-1);

    // Pending requests waiting for callbacks
    private final Map<Integer, CompletableFuture<Map<String, Object>>> pendingAccountRequests = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<OrderState>> pendingOrders = new ConcurrentHashMap<>();
    private final Map<Integer, OrderStatusInfo> orderStatuses = new ConcurrentHashMap<>();

    // Connection state
    private volatile boolean connected = false;
    private volatile String accountSummary = null;

    public IbkrEventWrapper(String userTag) {
        this.userTag = userTag;
    }

    public boolean isConnected() {
        return connected;
    }

    public int getNextValidOrderId() {
        return nextOrderId.getAndIncrement();
    }

    public boolean hasNextOrderId() {
        return nextOrderId.get() > 0;
    }

    public OrderStatusInfo getOrderStatus(int orderId) {
        return orderStatuses.get(orderId);
    }

    // ============ Connection Callbacks ============

    @Override
    public void connectAck() {
        log.info("[{}] IBKR connection acknowledged", userTag);
    }

    @Override
    public void nextValidId(int orderId) {
        log.info("[{}] Received nextValidId={}", userTag, orderId);
        nextOrderId.set(orderId);
        connected = true;
    }

    @Override
    public void connectionClosed() {
        log.warn("[{}] IBKR connection closed", userTag);
        connected = false;
    }

    // ============ Error Callback (TWS API 10.30+) ============

    @Override
    public void error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        // Codes 2104, 2106, 2158 are info messages (data farm connections)
        if (errorCode == 2104 || errorCode == 2106 || errorCode == 2158
                || errorCode == 2107 || errorCode == 2103 || errorCode == 2168 || errorCode == 2169) {
            log.debug("[{}] IBKR info [{}]: {}", userTag, errorCode, errorMsg);
            return;
        }

        log.error("[{}] IBKR error - id={}, code={}, msg={}", userTag, id, errorCode, errorMsg);

        // Mark connection broken on critical errors
        if (errorCode == 1100 || errorCode == 1101 || errorCode == 1102 || errorCode == 504) {
            connected = false;
        }

        // Fail any pending future for this id
        CompletableFuture<Map<String, Object>> accountFuture = pendingAccountRequests.remove(id);
        if (accountFuture != null) {
            accountFuture.completeExceptionally(
                new IbkrException("IBKR error [" + errorCode + "]: " + errorMsg));
        }

        CompletableFuture<OrderState> orderFuture = pendingOrders.remove(id);
        if (orderFuture != null) {
            orderFuture.completeExceptionally(
                new IbkrException("Order error [" + errorCode + "]: " + errorMsg));
        }
    }

    // ============ Order Status Callbacks ============

    @Override
    public void orderStatus(int orderId, String status, Decimal filled, Decimal remaining,
                            double avgFillPrice, long permId, int parentId,
                            double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
        log.info("[{}] OrderStatus orderId={} status={} filled={} avgPrice={}",
                userTag, orderId, status, filled, avgFillPrice);

        OrderStatusInfo info = new OrderStatusInfo(
                orderId, status,
                filled != null ? filled.value().doubleValue() : 0,
                remaining != null ? remaining.value().doubleValue() : 0,
                avgFillPrice, permId, parentId);
        orderStatuses.put(orderId, info);
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        log.info("[{}] OpenOrder orderId={} symbol={} status={}",
                userTag, orderId, contract.symbol(), orderState.getStatus());

        CompletableFuture<OrderState> future = pendingOrders.remove(orderId);
        if (future != null) {
            future.complete(orderState);
        }
    }

    @Override
    public void openOrderEnd() {
        log.debug("[{}] OpenOrderEnd", userTag);
    }

    // ============ Account Summary ============

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
        log.debug("[{}] AccountSummary reqId={} {}={} {}", userTag, reqId, tag, value, currency);
        // Aggregate by reqId
    }

    @Override
    public void accountSummaryEnd(int reqId) {
        log.debug("[{}] AccountSummaryEnd reqId={}", userTag, reqId);
    }

    // ============ Helper data class ============

    public record OrderStatusInfo(
            int orderId,
            String status,
            double filled,
            double remaining,
            double avgFillPrice,
            long permId,
            int parentId
    ) {}
}
