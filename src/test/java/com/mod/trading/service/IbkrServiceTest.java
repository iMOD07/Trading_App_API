package com.mod.trading.service;

import com.ib.client.EClientSocket;
import com.mod.trading.config.TradeWebSocketHandler;
import com.mod.trading.entity.Role;
import com.mod.trading.entity.TradeOrder;
import com.mod.trading.entity.User;
import com.mod.trading.exception.InvalidTradeRequestException;
import com.mod.trading.exception.TradingDisabledException;
import com.mod.trading.exception.UserNotFoundException;
import com.mod.trading.ibkr.IbkrConnectionManager;
import com.mod.trading.ibkr.IbkrEventWrapper;
import com.mod.trading.ibkr.IbkrException;
import com.mod.trading.model.TradeRequest;
import com.mod.trading.repository.TradeOrderRepository;
import com.mod.trading.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IbkrServiceTest {

    @Mock private TradeOrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private IbkrConnectionManager connectionManager;
    @Mock private IbkrEventWrapper eventWrapper;
    @Mock private TradeWebSocketHandler webSocketHandler;
    @Mock private EClientSocket clientSocket;

    @InjectMocks
    private IbkrService service;

    private User user;
    private final AtomicInteger orderIdCounter = new AtomicInteger(1000);

    @BeforeEach
    void setup() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(Role.USER);
        user.setActive(true);
        user.setTradingEnabled(true);
        user.setIbkrAccountId("DU1234567");
        user.setTradeAmount(new BigDecimal("500"));
        user.setRangeValue(new BigDecimal("0.01"));
        user.setProfitPercent(new BigDecimal("6"));
        user.setDailyLossLimit(new BigDecimal("1000"));
    }

    @Test
    void placeOrder_shouldThrow_whenUserNotFound() {
        when(userRepository.findByUsername("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeOrder(buildReq("AAPL", "180", "175"), "nope"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void placeOrder_shouldThrow_whenIbkrNotConnected() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(connectionManager.isConnected()).thenReturn(false);

        assertThatThrownBy(() -> service.placeOrder(buildReq("AAPL", "180", "175"), "testuser"))
                .isInstanceOf(IbkrException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void placeOrder_shouldThrow_whenTradingDisabled() {
        user.setTradingEnabled(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(connectionManager.isConnected()).thenReturn(true);
        when(eventWrapper.isReady()).thenReturn(true);

        assertThatThrownBy(() -> service.placeOrder(buildReq("AAPL", "180", "175"), "testuser"))
                .isInstanceOf(TradingDisabledException.class);
    }

    @Test
    void placeOrder_shouldThrow_whenStopLossAboveEntry() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(connectionManager.isConnected()).thenReturn(true);
        when(eventWrapper.isReady()).thenReturn(true);
        when(orderRepository.sumPotentialLossesToday(any(), any())).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.placeOrder(buildReq("AAPL", "180", "185"), "testuser"))
                .isInstanceOf(InvalidTradeRequestException.class)
                .hasMessageContaining("Stop loss");
    }

    @Test
    void placeOrder_shouldThrow_whenQuantityWouldBeZero() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(connectionManager.isConnected()).thenReturn(true);
        when(eventWrapper.isReady()).thenReturn(true);
        when(orderRepository.sumPotentialLossesToday(any(), any())).thenReturn(BigDecimal.ZERO);

        // $500 / $1000 share price = 0 shares
        assertThatThrownBy(() -> service.placeOrder(buildReq("BRK", "1000", "950"), "testuser"))
                .isInstanceOf(InvalidTradeRequestException.class)
                .hasMessageContaining("insufficient");
    }

    @Test
    void placeOrder_shouldThrow_whenDailyLossLimitReached() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(connectionManager.isConnected()).thenReturn(true);
        when(eventWrapper.isReady()).thenReturn(true);
        when(orderRepository.sumPotentialLossesToday(any(), any()))
                .thenReturn(new BigDecimal("1500"));

        assertThatThrownBy(() -> service.placeOrder(buildReq("AAPL", "180", "175"), "testuser"))
                .isInstanceOf(TradingDisabledException.class)
                .hasMessageContaining("Daily loss limit");
    }

    @Test
    void placeOrder_shouldSubmitBracket_andStoreThreeOrderIds() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(connectionManager.isConnected()).thenReturn(true);
        when(eventWrapper.isReady()).thenReturn(true);
        when(connectionManager.getClient()).thenReturn(clientSocket);
        when(orderRepository.sumPotentialLossesToday(any(), any())).thenReturn(BigDecimal.ZERO);

        when(eventWrapper.allocateOrderId())
                .thenAnswer(inv -> orderIdCounter.getAndIncrement());

        // Simulate IBKR acknowledging the parent immediately
        CompletableFuture<IbkrEventWrapper.OrderAck> ackFuture = CompletableFuture.completedFuture(
                new IbkrEventWrapper.OrderAck(1000, "PreSubmitted", new BigDecimal("0"), 12345L));
        when(eventWrapper.registerPendingOrder(anyInt())).thenReturn(ackFuture);

        ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
        when(orderRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        TradeRequest req = buildReq("AAPL", "180", "175");
        service.placeOrder(req, "testuser");

        TradeOrder saved = captor.getValue();
        assertThat(saved.getQty()).isEqualTo(2); // 500 / 180 = 2.77 -> 2
        assertThat(saved.getIbkrParentOrderId()).isNotNull();
        assertThat(saved.getIbkrTakeProfitOrderId()).isNotNull();
        assertThat(saved.getIbkrStopLossOrderId()).isNotNull();
        assertThat(saved.getIbkrPermId()).isEqualTo(12345L);
        assertThat(saved.getOrderStatus()).isEqualTo("PreSubmitted");
        assertThat(saved.getClientOrderId()).startsWith("TB-");

        // Verify three placeOrder calls (parent + take-profit + stop-loss)
        verify(clientSocket, times(3)).placeOrder(anyInt(), any(), any());

        verify(webSocketHandler).sendToUser(eq("testuser"), eq("ORDER_PLACED"), any());
    }

    @Test
    void placeOrder_shouldComputeTakeProfitCorrectly() {
        user.setProfitPercent(new BigDecimal("6"));
        user.setTradeAmount(new BigDecimal("1000"));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(connectionManager.isConnected()).thenReturn(true);
        when(eventWrapper.isReady()).thenReturn(true);
        when(connectionManager.getClient()).thenReturn(clientSocket);
        when(orderRepository.sumPotentialLossesToday(any(), any())).thenReturn(BigDecimal.ZERO);
        when(eventWrapper.allocateOrderId()).thenAnswer(inv -> orderIdCounter.getAndIncrement());

        CompletableFuture<IbkrEventWrapper.OrderAck> ackFuture = CompletableFuture.completedFuture(
                new IbkrEventWrapper.OrderAck(1000, "Submitted", BigDecimal.ZERO, 1L));
        when(eventWrapper.registerPendingOrder(anyInt())).thenReturn(ackFuture);

        ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
        when(orderRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // entry 100, profit 6% -> take profit 106.00
        service.placeOrder(buildReq("AAPL", "100", "95"), "testuser");

        assertThat(captor.getValue().getTakeProfit()).isEqualByComparingTo("106.00");
    }

    private TradeRequest buildReq(String symbol, String entry, String stop) {
        TradeRequest r = new TradeRequest();
        r.setSymbol(symbol);
        r.setEntryPrice(new BigDecimal(entry));
        r.setStopLoss(new BigDecimal(stop));
        return r;
    }
}
