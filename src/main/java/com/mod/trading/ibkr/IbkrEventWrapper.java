package com.mod.trading.ibkr;

import com.ib.client.*;
import com.mod.trading.event.IbkrOrderStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
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
 *
 * Status sync (A1): every {@link #orderStatus} callback is republished as a
 * Spring {@link IbkrOrderStatusEvent} so the listener can persist it
 * out-of-thread.
 */
@Slf4j
public class IbkrEventWrapper extends DefaultEWrapper {

    private final String userTag;
    private final AtomicInteger nextOrderId = new AtomicInteger(-1);
    private final ApplicationEventPublisher eventPublisher;

    // Pending requests waiting for callbacks
    private final Map<Integer, CompletableFuture<Map<String, Object>>> pendingAccountRequests = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<OrderState>> pendingOrders = new ConcurrentHashMap<>();
    private final Map<Integer, OrderStatusInfo> orderStatuses = new ConcurrentHashMap<>();

    // Connection state
    private volatile boolean connected = false;

    public IbkrEventWrapper(String userTag, ApplicationEventPublisher eventPublisher) {
        this.userTag = userTag;
        this.eventPublisher = eventPublisher;
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * Returns the next unused order id and increments the counter.
     * <b>NOTE:</b> callers placing a multi-leg bracket MUST call this inside
     * a {@code synchronized(wrapper)} block — see {@code IbkrService.placeOrder} (A2).
     */
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
        // Codes 2104, 2106, 2158 etc. are info messages (data farm connections)
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

        // Order rejection: IBKR sends error() with the order id, so we publish a
        // synthetic REJECTED event so the DB row is updated.
        // Codes commonly seen for order rejects: 201 (rejected), 202 (cancelled),
        // 203 (security not available), 10147..10159 (various rejects).
        if (id > 0 && (errorCode == 201 || errorCode == 202 || errorCode == 203
                || (errorCode >= 10000 && errorCode < 11000))) {
            String synthetic = (errorCode == 202) ? "Cancelled" : "Rejected";
            publishStatus(id, synthetic, BigDecimal.ZERO, null, null, null, 0L);
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

        BigDecimal filledBd = filled != null ? new BigDecimal(filled.value().toString()) : BigDecimal.ZERO;
        BigDecimal remainingBd = remaining != null ? new BigDecimal(remaining.value().toString()) : null;
        BigDecimal avgBd = avgFillPrice > 0 ? BigDecimal.valueOf(avgFillPrice) : null;
        BigDecimal lastBd = lastFillPrice > 0 ? BigDecimal.valueOf(lastFillPrice) : null;

        // Keep in-memory map for fast diagnostics
        orderStatuses.put(orderId, new OrderStatusInfo(
                orderId, status,
                filledBd.doubleValue(),
                remainingBd != null ? remainingBd.doubleValue() : 0,
                avgFillPrice, permId, parentId));

        // Republish to Spring event bus → IbkrOrderStatusListener persists to DB (A1).
        publishStatus(orderId, status, filledBd, remainingBd, avgBd, lastBd, permId);
    }

    private void publishStatus(int orderId, String status, BigDecimal filled,
                               BigDecimal remaining, BigDecimal avgFill,
                               BigDecimal lastFill, long permId) {
        if (eventPublisher == null) {
            log.warn("[{}] No event publisher wired; status update for orderId={} dropped", userTag, orderId);
            return;
        }
        try {
            eventPublisher.publishEvent(new IbkrOrderStatusEvent(
                    orderId, status, filled, remaining, avgFill, lastFill, permId, userTag));
        } catch (Exception e) {
            // NEVER let a publish failure kill the IBKR reader thread
            log.error("[{}] Failed to publish status event for orderId={}", userTag, orderId, e);
        }
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
