package com.mod.trading.service;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.mod.trading.entity.TradeOrder;
import com.mod.trading.entity.TradeOrder.Status;
import com.mod.trading.entity.User;
import com.mod.trading.ibkr.IbkrConnectionManager;
import com.mod.trading.ibkr.IbkrConnectionPool;
import com.mod.trading.ibkr.IbkrEventWrapper;
import com.mod.trading.ibkr.IbkrException;
import com.mod.trading.model.TradeRequest;
import com.mod.trading.repository.TradeOrderRepository;
import com.mod.trading.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IbkrService {

    private final IbkrConnectionPool connectionPool;
    private final TradeOrderRepository orderRepository;
    private final UserRepository userRepository;

    /**
     * Place a bracket order for a user.
     *
     * IBKR bracket = 3 linked orders:
     * 1. Parent (entry) - STP LMT BUY
     * 2. Child 1 (take profit) - LMT SELL
     * 3. Child 2 (stop loss) - STP SELL
     *
     * Only the LAST child has transmit=true; this submits all 3 atomically.
     *
     * Order of operations (A4 + A5):
     * 1. Validate inputs
     * 2. Check idempotency (A3 — clientOrderId)
     * 3. Reserve 3 IBKR order ids under a wrapper lock (A2)
     * 4. INSERT row in DB with status=PENDING  ← DB FIRST
     * 5. Submit bracket to IBKR
     *    - on failure → mark FAILED_SUBMIT, rethrow
     * 6. Update row to status=SUBMITTED
     * 7. Subsequent status changes are persisted by IbkrOrderStatusListener (A1)
     */
    public TradeOrder placeOrder(TradeRequest request, String username) {
        User user = getUser(username);

        // ===== 1. Validate (A6 — server-side) =====
        String symbol = request.getSymbol().toUpperCase().trim();
        BigDecimal entryPrice = request.getEntryPrice();
        BigDecimal stopLoss = request.getStopLoss();

        if (stopLoss.compareTo(entryPrice) >= 0) {
            throw new IbkrException("Stop loss (" + stopLoss + ") must be below entry price (" + entryPrice + ")");
        }

        BigDecimal tradeAmount = user.getTradeAmount();
        BigDecimal range = user.getRangeValue();
        BigDecimal profitPct = user.getProfitPercent();

        int qty = tradeAmount.divide(entryPrice, 0, RoundingMode.FLOOR).intValue();
        if (qty <= 0) {
            throw new IbkrException("Trade amount " + tradeAmount + " too small for entry price " + entryPrice);
        }

        BigDecimal stopPrice = round(entryPrice.add(range));
        BigDecimal limitPrice = round(entryPrice.add(range.multiply(BigDecimal.valueOf(2))));
        BigDecimal takeProfit = round(entryPrice.multiply(
                BigDecimal.ONE.add(profitPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))));

        if (takeProfit.compareTo(stopPrice) <= 0) {
            throw new IbkrException("Computed take profit (" + takeProfit
                    + ") is below stop price (" + stopPrice + "). Check profit % vs range.");
        }

        // ===== 2. Idempotency (A3) =====
        String clientOrderId = request.getClientOrderId();
        if (clientOrderId == null || clientOrderId.isBlank()) {
            // Server-generated fallback, but we strongly prefer client-generated for retry safety
            clientOrderId = UUID.randomUUID().toString();
        }
        var existing = orderRepository.findByClientOrderId(clientOrderId);
        if (existing.isPresent()) {
            log.info("[{}] Idempotent replay for clientOrderId={}, returning existing order id={}",
                    username, clientOrderId, existing.get().getId());
            return existing.get();
        }

        // ===== 3. Get connection & reserve order IDs under wrapper lock (A2) =====
        IbkrConnectionManager conn = connectionPool.getConnection(user);
        IbkrEventWrapper wrapper = conn.getWrapper();

        int parentId, takeProfitId, stopLossId;
        synchronized (wrapper) {
            parentId      = wrapper.getNextValidOrderId();
            takeProfitId  = wrapper.getNextValidOrderId();
            stopLossId    = wrapper.getNextValidOrderId();
        }

        log.info("[{}] Placing order: symbol={} qty={} stopPrice={} limitPrice={} tp={} sl={} parentId={}",
                username, symbol, qty, stopPrice, limitPrice, takeProfit, stopLoss, parentId);

        // ===== 4. INSERT row in DB FIRST (A4) =====
        TradeOrder order = persistPending(user, symbol, qty, entryPrice, tradeAmount, profitPct,
                stopPrice, limitPrice, takeProfit, stopLoss,
                parentId, takeProfitId, stopLossId, clientOrderId);

        // ===== 5. Submit to IBKR =====
        try {
            Contract contract = new Contract();
            contract.symbol(symbol);
            contract.secType("STK");
            contract.exchange("SMART");
            contract.currency("USD");

            // Parent: STP LMT BUY (transmit=false)
            Order parent = new Order();
            parent.orderId(parentId);
            parent.action("BUY");
            parent.orderType("STP LMT");
            parent.totalQuantity(Decimal.get(qty));
            parent.auxPrice(stopPrice.doubleValue());
            parent.lmtPrice(limitPrice.doubleValue());
            parent.account(user.getIbkrAccountId());
            parent.tif("GTC");
            parent.transmit(false);

            // Take Profit: LMT SELL (transmit=false)
            Order tp = new Order();
            tp.orderId(takeProfitId);
            tp.action("SELL");
            tp.orderType("LMT");
            tp.totalQuantity(Decimal.get(qty));
            tp.lmtPrice(takeProfit.doubleValue());
            tp.parentId(parentId);
            tp.account(user.getIbkrAccountId());
            tp.tif("GTC");
            tp.transmit(false);

            // Stop Loss: STP SELL (transmit=true → submits all 3)
            Order sl = new Order();
            sl.orderId(stopLossId);
            sl.action("SELL");
            sl.orderType("STP");
            sl.totalQuantity(Decimal.get(qty));
            sl.auxPrice(stopLoss.doubleValue());
            sl.parentId(parentId);
            sl.account(user.getIbkrAccountId());
            sl.tif("GTC");
            sl.transmit(true);

            conn.getClientSocket().placeOrder(parentId, contract, parent);
            conn.getClientSocket().placeOrder(takeProfitId, contract, tp);
            conn.getClientSocket().placeOrder(stopLossId, contract, sl);

        } catch (Exception e) {
            log.error("[{}] IBKR submission failed for order id={}", username, order.getId(), e);
            markFailedSubmit(order.getId(), e.getMessage());
            throw new IbkrException("Failed to submit order to IBKR: " + e.getMessage(), e);
        }

        // ===== 6. Mark SUBMITTED =====
        return markSubmitted(order.getId());
    }

    /**
     * Persist initial PENDING row in its own short transaction so it's
     * guaranteed-flushed BEFORE we touch IBKR.
     * Uses REQUIRES_NEW so the surrounding (non-transactional) call's
     * lifecycle has zero effect on this insert.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected TradeOrder persistPending(User user, String symbol, int qty,
                                        BigDecimal entryPrice, BigDecimal tradeAmount,
                                        BigDecimal profitPct, BigDecimal stopPrice,
                                        BigDecimal limitPrice, BigDecimal takeProfit,
                                        BigDecimal stopLoss, int parentId,
                                        int takeProfitId, int stopLossId,
                                        String clientOrderId) {
        TradeOrder order = new TradeOrder();
        order.setUser(user);
        order.setSymbol(symbol);
        order.setQty(qty);
        order.setEntryPrice(entryPrice);
        order.setTradeAmount(tradeAmount);
        order.setProfitPercent(profitPct);
        order.setStopPrice(stopPrice);
        order.setLimitPrice(limitPrice);
        order.setTakeProfit(takeProfit);
        order.setStopLoss(stopLoss);
        order.setIbkrParentOrderId(parentId);
        order.setIbkrTakeProfitOrderId(takeProfitId);
        order.setIbkrStopLossOrderId(stopLossId);
        order.setOrderStatus(Status.PENDING);
        order.setClientOrderId(clientOrderId);
        try {
            return orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            // Race: another request with same clientOrderId committed between our check & insert.
            // Re-read it and return; this is the idempotent outcome.
            return orderRepository.findByClientOrderId(clientOrderId)
                    .orElseThrow(() -> new IbkrException("Idempotency conflict: " + e.getMessage()));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected TradeOrder markSubmitted(Long orderId) {
        TradeOrder o = orderRepository.findById(orderId)
                .orElseThrow(() -> new IbkrException("Order vanished: id=" + orderId));
        // Don't downgrade if a status callback already raced ahead of us
        if (Status.PENDING.equals(o.getOrderStatus())) {
            o.setOrderStatus(Status.SUBMITTED);
            orderRepository.save(o);
        }
        return o;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markFailedSubmit(Long orderId, String reason) {
        orderRepository.findById(orderId).ifPresent(o -> {
            o.setOrderStatus(Status.FAILED_SUBMIT);
            o.setIbkrStatusRaw("Failed: " + truncate(reason, 40));
            orderRepository.save(o);
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * Cancel a previously placed order.
     * (A5) We do NOT mark CANCELLED here — the IBKR callback will do that
     * via {@code IbkrOrderStatusListener}. We only set PENDING_CANCEL as a hint.
     */
    @Transactional
    public void cancelOrder(Long orderId, String username) {
        User user = getUser(username);
        TradeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IbkrException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new IbkrException("Order does not belong to user");
        }

        if (Status.isTerminal(order.getOrderStatus())) {
            throw new IbkrException("Order is already in terminal state: " + order.getOrderStatus());
        }

        IbkrConnectionManager conn = connectionPool.getConnection(user);
        try {
            // Cancelling parent cascades to children
            conn.getClientSocket().cancelOrder(order.getIbkrParentOrderId(),
                    new com.ib.client.OrderCancel());

            // Hint only — final CANCELLED state will be set by the listener
            order.setOrderStatus(Status.PENDING_CANCEL);
            orderRepository.save(order);

            log.info("[{}] Cancel requested for order id={} (parentIbkrId={})",
                    username, orderId, order.getIbkrParentOrderId());
        } catch (Exception e) {
            throw new IbkrException("Failed to send cancel to IBKR: " + e.getMessage(), e);
        }
    }

    public List<TradeOrder> getAllOrders(String username) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(getUser(username).getId());
    }

    public List<TradeOrder> getOrdersBySymbol(String username, String symbol) {
        return orderRepository.findByUserIdAndSymbolOrderByCreatedAtDesc(
                getUser(username).getId(), symbol.toUpperCase());
    }

    public List<TradeOrder> getLastOrders(String username) {
        return orderRepository.findTop10ByUserIdOrderByCreatedAtDesc(getUser(username).getId());
    }

    public Map<String, Object> getSettings(String username) {
        User user = getUser(username);
        return Map.of(
                "tradeAmount", user.getTradeAmount(),
                "rangeValue", user.getRangeValue(),
                "profitPercent", user.getProfitPercent(),
                "ibkrAccountId", user.getIbkrAccountId() != null ? user.getIbkrAccountId() : "",
                "ibkrPaperTrading", user.isIbkrPaperTrading()
        );
    }

    @Transactional
    public void updateSettings(String username, BigDecimal tradeAmount,
                               BigDecimal rangeValue, BigDecimal profitPercent) {
        User user = getUser(username);
        if (tradeAmount != null) user.setTradeAmount(tradeAmount);
        if (rangeValue != null) user.setRangeValue(rangeValue);
        if (profitPercent != null) user.setProfitPercent(profitPercent);
        userRepository.save(user);
    }

    public Map<String, Object> testConnection(String username) {
        User user = getUser(username);
        try {
            IbkrConnectionManager conn = connectionPool.getConnection(user);
            return Map.of(
                    "connected", conn.isConnected(),
                    "host", user.getIbkrHost(),
                    "port", user.getIbkrPort(),
                    "accountId", user.getIbkrAccountId(),
                    "paperTrading", user.isIbkrPaperTrading()
            );
        } catch (Exception e) {
            return Map.of(
                    "connected", false,
                    "error", e.getMessage()
            );
        }
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IbkrException("User not found: " + username));
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
