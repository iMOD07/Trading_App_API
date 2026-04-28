package com.mod.trading.service;

import com.ib.client.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * يمثّل اتصال يوزر واحد بـ IB Gateway الخاص فيه
 */
@Slf4j
public class IbkrConnection implements EWrapper {

    @Getter private final Long userId;
    @Getter private final String username;
    @Getter private final String host;
    @Getter private final int port;

    private EClientSocket socket;
    private final EJavaSignal signal = new EJavaSignal();
    private final AtomicInteger nextOrderId = new AtomicInteger();
    private final AtomicInteger reqIdCounter = new AtomicInteger(1000);

    private final ConcurrentHashMap<Integer, CompletableFuture<String>> orderFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<Map<String, String>>> accountFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Map<String, String>> accountData = new ConcurrentHashMap<>();

    private volatile CompletableFuture<List<Map<String, Object>>> openOrdersFuture;
    private final List<Map<String, Object>> openOrdersList = Collections.synchronizedList(new ArrayList<>());

    public IbkrConnection(Long userId, String username, String host, int port) {
        this.userId   = userId;
        this.username = username;
        this.host     = host;
        this.port     = port;
    }

    // ─── Connect ──────────────────────────────────────────────────────────────

    public void connect(int clientId) {
        socket = new EClientSocket(this, signal);
        socket.eConnect(host, port, clientId);

        EReader reader = new EReader(socket, signal);
        reader.start();

        Thread t = new Thread(() -> {
            while (socket.isConnected()) {
                signal.waitForSignal();
                try { reader.processMsgs(); }
                catch (Exception e) { log.error("[{}] Reader error: {}", username, e.getMessage()); }
            }
        }, "ibkr-" + username);
        t.setDaemon(true);
        t.start();

        log.info("🔌 [{}] Connecting to {}:{}", username, host, port);
    }

    public void disconnect() {
        if (socket != null && socket.isConnected()) {
            socket.eDisconnect();
            log.info("🔌 [{}] Disconnected", username);
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    // ─── Order Methods ────────────────────────────────────────────────────────

    public int getNextOrderId() { return nextOrderId.getAndIncrement(); }
    public int getNextReqId()   { return reqIdCounter.getAndIncrement(); }

    public EClientSocket getSocket() { return socket; }

    public CompletableFuture<String> awaitOrderStatus(int orderId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        orderFutures.put(orderId, future);
        return future;
    }

    public CompletableFuture<Map<String, String>> requestAccountSummary() {
        int reqId = getNextReqId();
        CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
        accountFutures.put(reqId, future);
        accountData.put(reqId, new ConcurrentHashMap<>());
        socket.reqAccountSummary(reqId, "All",
                "NetLiquidation,TotalCashValue,BuyingPower,GrossPositionValue,AvailableFunds");
        return future;
    }

    public CompletableFuture<List<Map<String, Object>>> requestOpenOrders() {
        openOrdersList.clear();
        openOrdersFuture = new CompletableFuture<>();
        socket.reqOpenOrders();
        return openOrdersFuture;
    }

    // ─── EWrapper Callbacks ───────────────────────────────────────────────────

    @Override
    public void nextValidId(int orderId) {
        nextOrderId.set(orderId);
        log.info("✅ [{}] Connected — Next Order ID: {}", username, orderId);
    }

    @Override
    public void orderStatus(int orderId, String status, Decimal filled, Decimal remaining,
                            double avgFillPrice, long permId, int parentId, double lastFillPrice,
                            int clientId, String whyHeld, double mktCapPrice) {
        log.info("📋 [{}] Order {} → {}", username, orderId, status);
        CompletableFuture<String> future = orderFutures.get(orderId);
        if (future != null) {
            switch (status) {
                case "Submitted", "PreSubmitted", "Filled", "Cancelled" -> {
                    future.complete(status);
                    orderFutures.remove(orderId);
                }
            }
        }
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("orderId",   String.valueOf(orderId));
        entry.put("symbol",    contract.symbol());
        entry.put("action",    order.action().getApiString());
        entry.put("orderType", order.orderType().getApiString());
        entry.put("qty",       order.totalQuantity().toString());
        entry.put("status",    orderState.status());
        openOrdersList.add(entry);
    }

    @Override
    public void openOrderEnd() {
        CompletableFuture<List<Map<String, Object>>> f = openOrdersFuture;
        if (f != null) f.complete(new ArrayList<>(openOrdersList));
    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
        Map<String, String> data = accountData.get(reqId);
        if (data != null) data.put(tag, value);
    }

    @Override
    public void accountSummaryEnd(int reqId) {
        CompletableFuture<Map<String, String>> future = accountFutures.remove(reqId);
        Map<String, String> data = accountData.remove(reqId);
        if (future != null) future.complete(data != null ? data : new HashMap<>());
        if (socket.isConnected()) socket.cancelAccountSummary(reqId);
    }

    @Override public void error(Exception e) { log.error("[{}] Exception: {}", username, e.getMessage()); }
    @Override public void error(String str)   { log.warn("[{}] Message: {}", username, str); }
    @Override
    public void error(int id, long errorCode, int errorSource, String errorMsg, String advancedOrderRejectJson) {
        log.error("[{}] Error id={} code={} msg={}", username, id, errorCode, errorMsg);
        if (id > 0) {
            CompletableFuture<String> f = orderFutures.remove(id);
            if (f != null) f.completeExceptionally(new RuntimeException("IBKR [" + errorCode + "]: " + errorMsg));
        }
    }

    // ─── EWrapper Stubs ───────────────────────────────────────────────────────
    @Override public void tickPrice(int t, int f, double p, TickAttrib a) {}
    @Override public void tickSize(int t, int f, Decimal s) {}
    @Override public void tickOptionComputation(int a, int b, int c, double d, double e, double f, double g, double h, double i, double j, double k) {}
    @Override public void tickGeneric(int t, int tt, double v) {}
    @Override public void tickString(int t, int tt, String v) {}
    @Override public void tickEFP(int a, int b, double c, String d, double e, int f, String g, double h, double i) {}
    @Override public void updateAccountValue(String k, String v, String c, String a) {}
    @Override public void updatePortfolio(Contract c, Decimal p, double mp, double mv, double ac, double up, double rp, String a) {}
    @Override public void updateAccountTime(String t) {}
    @Override public void accountDownloadEnd(String a) {}
    @Override public void contractDetails(int r, ContractDetails c) {}
    @Override public void bondContractDetails(int r, ContractDetails c) {}
    @Override public void contractDetailsEnd(int r) {}
    @Override public void execDetails(int r, Contract c, Execution e) {}
    @Override public void execDetailsEnd(int r) {}
    @Override public void updateMktDepth(int t, int p, int o, int s, double pr, Decimal sz) {}
    @Override public void updateMktDepthL2(int t, int p, String m, int o, int s, double pr, Decimal sz, boolean i) {}
    @Override public void updateNewsBulletin(int m, int mt, String msg, String o) {}
    @Override public void managedAccounts(String a) { log.info("[{}] Managed accounts: {}", username, a); }
    @Override public void receiveFA(int f, String x) {}
    @Override public void historicalData(int r, Bar b) {}
    @Override public void historicalDataUpdate(int r, Bar b) {}
    @Override public void historicalDataEnd(int r, String s, String e) {}
    @Override public void scannerParameters(String x) {}
    @Override public void scannerData(int r, int rk, ContractDetails c, String d, String b, String p, String l) {}
    @Override public void scannerDataEnd(int r) {}
    @Override public void realtimeBar(int r, long t, double o, double h, double l, double c, Decimal v, Decimal w, int ct) {}
    @Override public void currentTime(long t) {}
    @Override public void currentTimeInMillis(long t) {}
    @Override public void fundamentalData(int r, String d) {}
    @Override public void deltaNeutralValidation(int r, DeltaNeutralContract d) {}
    @Override public void tickSnapshotEnd(int r) {}
    @Override public void marketDataType(int r, int m) {}
    @Override public void commissionAndFeesReport(CommissionAndFeesReport c) {}
    @Override public void position(String a, Contract c, Decimal p, double avg) {}
    @Override public void positionEnd() {}
    @Override public void verifyMessageAPI(String a) {}
    @Override public void verifyCompleted(boolean s, String e) {}
    @Override public void verifyAndAuthMessageAPI(String a, String x) {}
    @Override public void verifyAndAuthCompleted(boolean s, String e) {}
    @Override public void displayGroupList(int r, String g) {}
    @Override public void displayGroupUpdated(int r, String c) {}
    @Override public void connectionClosed() { log.warn("⚠️ [{}] Connection closed", username); }
    @Override public void connectAck()       { log.info("✅ [{}] Connection acknowledged", username); }
    @Override public void positionMulti(int r, String a, String m, Contract c, Decimal p, double avg) {}
    @Override public void positionMultiEnd(int r) {}
    @Override public void accountUpdateMulti(int r, String a, String m, String k, String v, String c) {}
    @Override public void accountUpdateMultiEnd(int r) {}
    @Override public void securityDefinitionOptionalParameter(int r, String e, int u, String t, String m, Set<String> ex, Set<Double> s) {}
    @Override public void securityDefinitionOptionalParameterEnd(int r) {}
    @Override public void softDollarTiers(int r, SoftDollarTier[] t) {}
    @Override public void familyCodes(FamilyCode[] f) {}
    @Override public void symbolSamples(int r, ContractDescription[] c) {}
    @Override public void mktDepthExchanges(DepthMktDataDescription[] d) {}
    @Override public void tickNews(int t, long ts, String p, String a, String h, String e) {}
    @Override public void smartComponents(int r, Map<Integer, Map.Entry<String, Character>> m) {}
    @Override public void tickReqParams(int t, double m, String b, int s) {}
    @Override public void newsProviders(NewsProvider[] n) {}
    @Override public void newsArticle(int r, int t, String a) {}
    @Override public void historicalNews(int r, String t, String p, String a, String h) {}
    @Override public void historicalNewsEnd(int r, boolean h) {}
    @Override public void headTimestamp(int r, String h) {}
    @Override public void histogramData(int r, List<HistogramEntry> i) {}
    @Override public void rerouteMktDataReq(int r, int c, String e) {}
    @Override public void rerouteMktDepthReq(int r, int c, String e) {}
    @Override public void marketRule(int m, PriceIncrement[] p) {}
    @Override public void pnl(int r, double d, double u, double rl) {}
    @Override public void pnlSingle(int r, Decimal p, double d, double u, double rl, double v) {}
    @Override public void historicalTicks(int r, List<HistoricalTick> t, boolean d) {}
    @Override public void historicalTicksBidAsk(int r, List<HistoricalTickBidAsk> t, boolean d) {}
    @Override public void historicalTicksLast(int r, List<HistoricalTickLast> t, boolean d) {}
    @Override public void tickByTickAllLast(int r, int t, long ts, double p, Decimal s, TickAttribLast a, String e, String sc) {}
    @Override public void tickByTickBidAsk(int r, long t, double bp, double ap, Decimal bs, Decimal as2, TickAttribBidAsk a) {}
    @Override public void tickByTickMidPoint(int r, long t, double m) {}
    @Override public void orderBound(long o, int a, int ap) {}
    @Override public void completedOrder(Contract c, Order o, OrderState os) {}
    @Override public void completedOrdersEnd() {}
    @Override public void replaceFAEnd(int r, String t) {}
    @Override public void wshMetaData(int r, String d) {}
    @Override public void wshEventData(int r, String d) {}
    @Override public void historicalSchedule(int r, String s, String e, String t, List<HistoricalSession> ss) {}
    @Override public void userInfo(int r, String w) {}
    @Override public void orderStatusProtoBuf(com.ib.client.protobuf.OrderStatusProto.OrderStatus o) {}
    @Override public void openOrderProtoBuf(com.ib.client.protobuf.OpenOrderProto.OpenOrder o) {}
    @Override public void openOrdersEndProtoBuf(com.ib.client.protobuf.OpenOrdersEndProto.OpenOrdersEnd o) {}
    @Override public void errorProtoBuf(com.ib.client.protobuf.ErrorMessageProto.ErrorMessage o) {}
    @Override public void execDetailsProtoBuf(com.ib.client.protobuf.ExecutionDetailsProto.ExecutionDetails o) {}
    @Override public void execDetailsEndProtoBuf(com.ib.client.protobuf.ExecutionDetailsEndProto.ExecutionDetailsEnd o) {}
}
