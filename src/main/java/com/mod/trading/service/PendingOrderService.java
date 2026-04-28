package com.mod.trading.service;

import com.mod.trading.entity.TradeOrder;
import com.mod.trading.model.request.TradeRequest;
import com.mod.trading.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * كل دقيقة يتحقق من الأوامر المعلقة (PENDING)
 * ولما يلاقي Gateway اليوزر online → ينفذ الأمر
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingOrderService {

    private final TradeOrderRepository orderRepository;
    private final IbkrConnectionPool connectionPool;
    private final IbkrService ibkrService;

    @Scheduled(fixedDelay = 60_000) // كل دقيقة
    public void processPendingOrders() {
        List<TradeOrder> pendingOrders = orderRepository.findByPendingTrue();

        if (pendingOrders.isEmpty()) return;

        log.info("🔄 Checking {} pending orders...", pendingOrders.size());

        for (TradeOrder order : pendingOrders) {
            Long userId = order.getUser().getId();

            // تحقق إذا Gateway اليوزر رجع online
            if (!connectionPool.isConnected(userId)) continue;

            log.info("✅ [{}] Gateway back online - executing pending order: {}",
                    order.getUser().getUsername(), order.getSymbol());

            try {
                // أنشئ TradeRequest من الأمر المحفوظ
                TradeRequest request = new TradeRequest();
                request.setSymbol(order.getSymbol());
                request.setEntryPrice(order.getEntryPrice());
                request.setStopLoss(order.getStopLoss());

                // احذف الـ PENDING وأرسل أمر جديد
                orderRepository.delete(order);
                ibkrService.placeOrder(request, order.getUser().getUsername());

                log.info("✅ Pending order executed for [{}]: {}",
                        order.getUser().getUsername(), order.getSymbol());

            } catch (Exception e) {
                log.error("❌ Failed to execute pending order for [{}]: {}",
                        order.getUser().getUsername(), e.getMessage());
            }
        }
    }
}
