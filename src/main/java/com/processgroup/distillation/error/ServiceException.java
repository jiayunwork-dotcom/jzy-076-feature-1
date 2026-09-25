package com.processgroup.distillation.error;

/**
 * 业务异常：由 {@link GlobalExceptionHandler} 统一翻译成结构化错误响应。
 */
public class ServiceException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public ServiceException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public ServiceException(ErrorCode errorCode, String detail) {
        super(errorCode.code() + ": " + detail);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String detail() {
        return detail;
    }
}
