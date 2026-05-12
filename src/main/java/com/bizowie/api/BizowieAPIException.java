package com.bizowie.api;

/** Thrown for fatal errors raised by the Bizowie API client. */
public class BizowieAPIException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BizowieAPIException(String message) {
        super(message);
    }

    public BizowieAPIException(String message, Throwable cause) {
        super(message, cause);
    }
}
