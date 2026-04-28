package com.mod.trading.ibkr;

public class IbkrException extends RuntimeException {

    public IbkrException(String message) {
        super(message);
    }

    public IbkrException(String message, Throwable cause) {
        super(message, cause);
    }
}
