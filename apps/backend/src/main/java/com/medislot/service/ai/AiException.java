package com.medislot.service.ai;

/**
 * AI 调用异常。
 */
public class AiException extends RuntimeException {

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
