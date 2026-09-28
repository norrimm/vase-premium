package com.example.trade.notify;

/** 通知の送信に失敗した。 */
public class NotifyException extends RuntimeException {

    public NotifyException(String message) {
        super(message);
    }

    public NotifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
