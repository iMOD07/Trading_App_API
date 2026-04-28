package com.mod.trading.service;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.mod.trading.entity.TradeOrder;
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
import org.springframework.stereotype.Service;
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
     */
    @Transactional
    public TradeOrder placeOrder(TradeRequest request, String username) {
        User user = getUser(username);
        IbkrConnectionManager conn = connectionPool.getConnection(user);
        IbkrEventWrapper wrapper = conn.getWrapper();

        String symbol = request.getSymbol().toUpperCase();
        BigDecimal entryPrice = request.getEntryPrice();
        BigDecimal stopLoss = request.getStopLoss();

        // Calculate from user settings
        BigDecimal tradeAmount = user.getTradeAmount();
        BigDecimal range = user.getRangeValue();
        BigDecimal profitPct = user.getProfitPercent();

        int qty = tradeAmount.divide(entryPrice, 0, RoundingMode.FLOOR).intValue();
        if (qty <= 0) {
            throw new IbkrException("Trade amount too small for entry price");
        }

        BigDecimal stopPrice = round(entryPrice.add(range));
        BigDecimal limitPrice = round(entryPrice.add(range.multiply(BigDecimal.valueOf(2))));
        BigDecimal takeProfit = round(entryPrice.multiply(
                BigDecimal.ONE.add(profitPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))));

        // Idempotency key
        String clientOrderId = UUID.randomUUID().toString();

        log.info("[{}] Placing order: symbol={} qty={} stopPrice={} limitPrice={} tp={} sl={}",
                username, symbol, qty, stopPrice, limitPrice, takeProfit, stopLoss);

        // Get 3 sequential order IDs
        int parentId = wrapper.getNextValidOrderId();
        int takeProfitId = wrapper.getNextValidOrderId();
        int stopLossId = wrapper.getNextValidOrderId();

        // Build contract
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.exchange("SMART");
        contract.currency("USD");

        // 1. Parent: STP LMT BUY (transmit=false)
        Order parent = new Order();
        parent.orderId(parentId);
        parent.action("BUY");
        parent.orderType("STP LMT");
        parent.totalQuantity(Decimal.get(qty));
        parent.auxPrice(stopPrice.doubleValue());      // Stop trigger
        parent.lmtPrice(limitPrice.doubleValue());     // Limit price
        parent.account(user.getIbkrAccountId());
        parent.tif("GTC");
        parent.transmit(false);

        // 2. Take Profit: LMT SELL (transmit=false)
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

        // 3. Stop Loss: STP SELL (transmit=true → submits all 3)
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

        // Submit
        try {
            conn.getClientSocket().placeOrder(parentId, contract, parent);
            conn.getClientSocket().placeOrder(takeProfitId, contract, tp);
            conn.getClientSocket().placeOrder(stopLossId, contract, sl);
        } catch (Exception e) {
            throw new IbkrException("Failed to submit order to IBKR: " + e.getMessage(), e);
        }

        // Save to DB
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
        order.setOrderStatus("SUBMITTED");
        order.setClientOrderId(clientOrderId);

        return orderRepository.save(order);
    }

    /**
     * Cancel a previously placed order.
     */
    @Transactional
    public void cancelOrder(Long orderId, String username) {
        User user = getUser(username);
        TradeOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IbkrException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new IbkrException("Order does not belong to user");
        }

        IbkrConnectionManager conn = connectionPool.getConnection(user);
        try {
            // Cancelling parent cascades to children
            conn.getClientSocket().cancelOrder(order.getIbkrParentOrderId(),
                    new com.ib.client.OrderCancel());
            order.setOrderStatus("CANCELLED");
            orderRepository.save(order);
            log.info("[{}] Cancelled order id={}", username, orderId);
        } catch (Exception e) {
            throw new IbkrException("Failed to cancel order: " + e.getMessage(), e);
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

    /**
     * Settings & account info
     */
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

    /**
     * Test the user's IBKR connection (for diagnostics).
     */
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
