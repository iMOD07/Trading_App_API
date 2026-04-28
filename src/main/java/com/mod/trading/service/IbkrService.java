package com.mod.trading.service;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.OrderCancel;
import com.mod.trading.entity.TradeOrder;
import com.mod.trading.entity.User;
import com.mod.trading.model.request.TradeRequest;
import com.mod.trading.repository.TradeOrderRepository;
import com.mod.trading.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IbkrService {

    private final TradeOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final IbkrConnectionPool connectionPool;

    // ─── Place Order ──────────────────────────────────────────────────────────

    public TradeOrder placeOrder(TradeRequest request, String username) throws Exception {
        User user = getUser(username);
        IbkrConnection conn = connectionPool.getConnection(user.getId());

        // لو Gateway offline → احفظ الأمر PENDING
        if (conn == null || !conn.isConnected()) {
            return savePendingOrder(request, user);
        }

        return executeOrder(request, user, conn);
    }

    // ─── Execute Order (Gateway online) ──────────────────────────────────────

    private TradeOrder executeOrder(TradeRequest request, User user, IbkrConnection conn) throws Exception {
        double tradeAmount = user.getTradeAmount();
        double range       = user.getRangeValue();
        double profitPct   = user.getProfitPercent();

        String symbol     = request.getSymbol().toUpperCase();
        int qty           = (int) Math.floor(tradeAmount / request.getEntryPrice());
        double stopPrice  = round(request.getEntryPrice() + range);
        double limitPrice = round(request.getEntryPrice() + (range * 2));
        double takeProfit = round(request.getEntryPrice() * (1 + profitPct / 100));
        double stopLoss   = request.getStopLoss();

        if (qty <= 0) throw new RuntimeException("Trade amount too small for this price.");

        log.info("📊 [{}] {} qty={} entry={} tp={} sl={}",
                user.getUsername(), symbol, qty, request.getEntryPrice(), takeProfit, stopLoss);

        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.exchange("SMART");
        contract.currency("USD");

        int parentId = conn.getNextOrderId();
        int tpId     = conn.getNextOrderId();
        int slId     = conn.getNextOrderId();

        Order parent = new Order();
        parent.orderId(parentId);
        parent.account(user.getIbkrAccount());
        parent.action("BUY");
        parent.orderType("STP LMT");
        parent.totalQuantity(Decimal.get(qty));
        parent.auxPrice(stopPrice);
        parent.lmtPrice(limitPrice);
        parent.tif("GTC");
        parent.transmit(false);

        Order tp = new Order();
        tp.orderId(tpId);
        tp.account(user.getIbkrAccount());
        tp.action("SELL");
        tp.orderType("LMT");
        tp.totalQuantity(Decimal.get(qty));
        tp.lmtPrice(takeProfit);
        tp.parentId(parentId);
        tp.tif("GTC");
        tp.transmit(false);

        Order sl = new Order();
        sl.orderId(slId);
        sl.account(user.getIbkrAccount());
        sl.action("SELL");
        sl.orderType("STP");
        sl.totalQuantity(Decimal.get(qty));
        sl.auxPrice(stopLoss);
        sl.parentId(parentId);
        sl.tif("GTC");
        sl.transmit(true);

        var statusFuture = conn.awaitOrderStatus(parentId);
        conn.getSocket().placeOrder(parentId, contract, parent);
        conn.getSocket().placeOrder(tpId,     contract, tp);
        conn.getSocket().placeOrder(slId,     contract, sl);

        String status = statusFuture.get(10, TimeUnit.SECONDS);
        log.info("✅ [{}] Order {} → {}", user.getUsername(), parentId, status);

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
        order.setIbkrOrderId(String.valueOf(parentId));
        order.setOrderStatus(status);
        order.setPending(false);
        order.setRawResponse("parentId=" + parentId + " tpId=" + tpId + " slId=" + slId);

        return orderRepository.save(order);
    }

    // ─── Save Pending Order (Gateway offline) ─────────────────────────────────

    private TradeOrder savePendingOrder(TradeRequest request, User user) {
        log.warn("⚠️ [{}] Gateway offline → saving PENDING order", user.getUsername());

        double tradeAmount = user.getTradeAmount();
        double range       = user.getRangeValue();
        double profitPct   = user.getProfitPercent();

        String symbol     = request.getSymbol().toUpperCase();
        int qty           = (int) Math.floor(tradeAmount / request.getEntryPrice());
        double stopPrice  = round(request.getEntryPrice() + range);
        double limitPrice = round(request.getEntryPrice() + (range * 2));
        double takeProfit = round(request.getEntryPrice() * (1 + profitPct / 100));

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
        order.setStopLoss(request.getStopLoss());
        order.setOrderStatus("PENDING");
        order.setPending(true);
        order.setRawResponse("Gateway offline - order queued");

        return orderRepository.save(order);
    }

    // ─── Cancel Order ─────────────────────────────────────────────────────────

    @Transactional
    public TradeOrder cancelOrder(String ibkrOrderId, String username) {
        TradeOrder order = orderRepository.findByIbkrOrderId(ibkrOrderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + ibkrOrderId));

        if (!order.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized");
        }

        IbkrConnection conn = connectionPool.getConnection(order.getUser().getId());
        if (conn == null || !conn.isConnected()) {
            throw new RuntimeException("Gateway offline - cannot cancel now");
        }

        conn.getSocket().cancelOrder(Integer.parseInt(ibkrOrderId), new OrderCancel());
        order.setOrderStatus("cancelled");
        return orderRepository.save(order);
    }

    // ─── Account Info ─────────────────────────────────────────────────────────

    public Map<String, Object> getAccount(String username) throws Exception {
        User user = getUser(username);
        IbkrConnection conn = connectionPool.getConnection(user.getId());

        if (conn == null || !conn.isConnected()) {
            throw new RuntimeException("Gateway offline - cannot fetch account");
        }

        Map<String, String> summary = conn.requestAccountSummary().get(10, TimeUnit.SECONDS);
        return new HashMap<>(summary);
    }

    // ─── Open Orders ──────────────────────────────────────────────────────────

    public Object getOpenOrders(String username) throws Exception {
        User user = getUser(username);
        IbkrConnection conn = connectionPool.getConnection(user.getId());

        if (conn == null || !conn.isConnected()) {
            throw new RuntimeException("Gateway offline");
        }

        return conn.requestOpenOrders().get(10, TimeUnit.SECONDS);
    }

    // ─── Connection Status ────────────────────────────────────────────────────

    public Map<String, Object> getConnectionStatus(String username) {
        User user = getUser(username);
        boolean connected = connectionPool.isConnected(user.getId());
        return Map.of(
                "connected", connected,
                "host",      user.getIbkrHost() != null ? user.getIbkrHost() : "",
                "port",      user.getIbkrPort()
        );
    }

    // ─── IBKR Settings ────────────────────────────────────────────────────────

    public void updateIbkrSettings(String username, String ibkrAccount, String ibkrHost, int ibkrPort, int ibkrClientId) {
        User user = getUser(username);
        user.setIbkrAccount(ibkrAccount);
        user.setIbkrHost(ibkrHost);
        user.setIbkrPort(ibkrPort);
        user.setIbkrClientId(ibkrClientId);
        userRepository.save(user);

        // أعد الاتصال بالإعدادات الجديدة
        connectionPool.openConnection(user);
        log.info("🔄 [{}] IBKR settings updated and reconnected", username);
    }

    // ─── Trade Settings ───────────────────────────────────────────────────────

    public Map<String, Object> getSettings(String username) {
        User user = getUser(username);
        return Map.of(
                "tradeAmount",   user.getTradeAmount(),
                "rangeValue",    user.getRangeValue(),
                "profitPercent", user.getProfitPercent()
        );
    }

    public void updateSettings(String username, double tradeAmount, double rangeValue, double profitPercent) {
        User user = getUser(username);
        user.setTradeAmount(tradeAmount);
        user.setRangeValue(rangeValue);
        user.setProfitPercent(profitPercent);
        userRepository.save(user);
    }

    // ─── Orders History ───────────────────────────────────────────────────────

    public List<TradeOrder> getAllOrders(String username) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(getUser(username).getId());
    }

    public List<TradeOrder> getLastOrders(String username) {
        return orderRepository.findTop10ByUserIdOrderByCreatedAtDesc(getUser(username).getId());
    }

    public List<TradeOrder> getOrdersBySymbol(String username, String symbol) {
        return orderRepository.findByUserIdAndSymbolOrderByCreatedAtDesc(
                getUser(username).getId(), symbol.toUpperCase());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
