package com.mod.trading.service;

import com.mod.trading.entity.TradeOrder;
import com.mod.trading.entity.TradeOrder.Status;
import com.mod.trading.event.IbkrOrderStatusEvent;
import com.mod.trading.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Listens for status callbacks from IBKR and persists them to the
 * {@code trade_orders} table. This is the bridge that closes the
 * "stale DB" gap (A1).
 *
 * Threading: runs on Spring's default async executor (NOT the IBKR
 * reader thread) so a slow DB write cannot back up message processing.
 *
 * Idempotency: same callback can arrive twice for the same orderId
 * (e.g. PreSubmitted → Submitted → Filled). We always overwrite with
 * the latest values, but never regress out of a terminal state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IbkrOrderStatusListener {

    private final TradeOrderRepository orderRepository;

    @Async
    @EventListener
    @Transactional
    public void onOrderStatus(IbkrOrderStatusEvent event) {
        Optional<TradeOrder> opt = orderRepository.findByAnyIbkrOrderId(event.getOrderId());

        if (opt.isEmpty()) {
            // Two legitimate cases:
            //  1. parent's child legs (TP/SL) — they ARE in our DB, will match
            //  2. orphan callback for an order we never persisted (shouldn't happen
            //     after A4, but log it loudly so we notice if it does)
            log.warn("[{}] Status callback for unknown ibkrOrderId={} status={} — no DB row",
                    event.getUserTag(), event.getOrderId(), event.getIbkrStatus());
            return;
        }

        TradeOrder order = opt.get();
        String oldStatus = order.getOrderStatus();

        // Refuse to regress out of terminal state.
        // Example: we already saw "Filled" then a stray "Cancelled" arrives for a leg.
        if (Status.isTerminal(oldStatus)) {
            log.debug("[{}] Ignoring late callback orderId={} status={} (already terminal: {})",
                    event.getUserTag(), event.getOrderId(), event.getIbkrStatus(), oldStatus);
            // We still update the raw status & permId for audit, but not orderStatus.
            order.setIbkrStatusRaw(event.getIbkrStatus());
            if (event.getPermId() > 0 && order.getIbkrPermId() == null) {
                order.setIbkrPermId(event.getPermId());
            }
            order.setLastIbkrUpdate(LocalDateTime.now());
            orderRepository.save(order);
            return;
        }

        // Map IBKR raw status → internal status
        String mapped = mapIbkrStatus(event.getIbkrStatus(),
                event.getFilled(), order.getQty());

        order.setIbkrStatusRaw(event.getIbkrStatus());
        order.setOrderStatus(mapped);
        order.setLastIbkrUpdate(LocalDateTime.now());

        if (event.getFilled() != null) {
            order.setFilledQty(event.getFilled());
        }
        if (event.getRemaining() != null) {
            order.setRemainingQty(event.getRemaining());
        }
        if (event.getAvgFillPrice() != null) {
            order.setAvgFillPrice(event.getAvgFillPrice());
        }
        if (event.getLastFillPrice() != null) {
            order.setLastFillPrice(event.getLastFillPrice());
        }
        if (event.getPermId() > 0 && order.getIbkrPermId() == null) {
            order.setIbkrPermId(event.getPermId());
        }

        orderRepository.save(order);

        if (!mapped.equals(oldStatus)) {
            log.info("[{}] Order id={} status: {} → {} (ibkr=\"{}\", filled={}/{})",
                    event.getUserTag(), order.getId(), oldStatus, mapped,
                    event.getIbkrStatus(), event.getFilled(), order.getQty());
        }
    }

    /**
     * Translate IBKR's status strings to our internal lifecycle.
     * Reference: <a href="https://interactivebrokers.github.io/tws-api/order_submission.html">IBKR docs</a>
     */
    private String mapIbkrStatus(String ibkr, BigDecimal filled, Integer totalQty) {
        if (ibkr == null) return Status.SUBMITTED;
        switch (ibkr) {
            case "PendingSubmit":
            case "PendingCancel":
                return Status.PENDING_CANCEL.equals(ibkr) ? Status.PENDING_CANCEL : Status.PENDING;
            case "PreSubmitted":
                return Status.PRESUBMITTED;
            case "Submitted":
                // Could be partial-fill in flight
                if (filled != null && totalQty != null
                        && filled.compareTo(BigDecimal.ZERO) > 0
                        && filled.compareTo(BigDecimal.valueOf(totalQty)) < 0) {
                    return Status.PARTIALLY_FILLED;
                }
                return Status.SUBMITTED;
            case "Filled":
                return Status.FILLED;
            case "Cancelled":
            case "ApiCancelled":
                return Status.CANCELLED;
            case "Inactive":
                // Inactive can mean rejected or unable-to-execute; treat as REJECTED
                return Status.REJECTED;
            case "Rejected":
                return Status.REJECTED;
            default:
                log.warn("Unknown IBKR status \"{}\" — defaulting to SUBMITTED", ibkr);
                return Status.SUBMITTED;
        }
    }
}

