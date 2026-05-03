package com.mod.trading.event;

import lombok.Value;

import java.math.BigDecimal;

/**
 * Published by {@code IbkrEventWrapper} whenever IBKR sends an
 * {@code orderStatus} callback. Consumed asynchronously by
 * {@code IbkrOrderStatusListener} which persists the new state to DB.
 *
 * Decoupling rationale: the wrapper runs on the IBKR reader thread —
 * we must NOT do a DB transaction there or we block message processing
 * (and risk swallowing IBKR exceptions inside JPA exceptions).
 */
@Value
public class IbkrOrderStatusEvent {
    /** IBKR-side order id; can be the parent, take-profit, or stop-loss leg. */
    int orderId;

    /** Raw IBKR status string: "Submitted", "PreSubmitted", "Filled", "Cancelled", "ApiCancelled", "Inactive", "PendingCancel"... */
    String ibkrStatus;

    BigDecimal filled;
    BigDecimal remaining;
    BigDecimal avgFillPrice;
    BigDecimal lastFillPrice;

    /** IBKR's permanent order id — stable across reconnects, useful for audit. */
    long permId;

    /** Tag for logging only ({@code username}). */
    String userTag;
}

