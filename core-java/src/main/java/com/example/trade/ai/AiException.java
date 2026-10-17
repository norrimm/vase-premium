package com.example.trade.ai;

/** AI サービスから推論結果を受け取れなかった。 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
