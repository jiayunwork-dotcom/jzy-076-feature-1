package com.processgroup.distillation.error;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.processgroup.distillation.service.validation.InputValidator.FieldValidationException;

import java.util.Map;

/**
 * 全局异常处理：业务异常、非法 JSON 一律翻译成统一的结构化 {@link ApiError}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleService(ServiceException ex) {
        Map<String, java.util.List<String>> fields =
                (ex.getCause() instanceof FieldValidationException fve) ? fve.fieldErrors() : null;
        ApiError body = new ApiError(
                ex.errorCode().code(),
                ex.errorCode().message(),
                ex.detail(),
                fields);
        return ResponseEntity.status(ex.errorCode().status()).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        ApiError body = new ApiError(ec.code(), ec.message(), "请求体不是合法 JSON，或数值字段格式错误", null);
        return ResponseEntity.status(ec.status()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        ApiError body = new ApiError(
                "INTERNAL_ERROR",
                "服务内部错误",
                ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                null);
        return ResponseEntity.internalServerError().body(body);
    }
}
