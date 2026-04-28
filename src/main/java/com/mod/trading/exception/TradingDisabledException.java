package com.mod.trading.exception;

public class TradingDisabledException extends BusinessException {
    public TradingDisabledException(String reason) {
        super("Trading is disabled: " + reason);
    }
}
