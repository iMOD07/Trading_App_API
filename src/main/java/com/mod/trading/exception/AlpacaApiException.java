package com.mod.trading.exception;

import lombok.Getter;

@Getter
public class AlpacaApiException extends BusinessException {
    private final int statusCode;

    public AlpacaApiException(int statusCode, String message) {
        super("Alpaca API error [" + statusCode + "]: " + message);
        this.statusCode = statusCode;
    }
}
