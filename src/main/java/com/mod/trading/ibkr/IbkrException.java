package com.mod.trading.ibkr;

import lombok.Getter;

@Getter
public class IbkrException extends RuntimeException {
    private final int errorCode;

    public IbkrException(int errorCode, String message) {
        super("IBKR [" + errorCode + "]: " + message);
        this.errorCode = errorCode;
    }
}
