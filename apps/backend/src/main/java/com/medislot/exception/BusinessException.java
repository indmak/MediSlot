package com.medislot.exception;

/**
 * 业务异常：表示可预期的业务错误（如号源已约满、重复预约），
 * 由 {@link com.medislot.web.GlobalExceptionHandler} 统一处理。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
