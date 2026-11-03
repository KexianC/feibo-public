package com.hzmwmt.common.handler;

import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.common.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理器。
 */
@RestControllerAdvice(basePackages = {
        "com.hzmwmt.controller",
        "com.hzmwmt.geo.controller",
        "com.hzmwmt.a_publish.controller",
        "com.hzmwmt.ai.rag.controller"
})
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatusException(ResponseStatusException exception,
                                                                       HttpServletRequest request) {
        log.warn("request rejected: status={}", exception.getStatusCode().value());
        String message = exception.getReason() == null ? "Request rejected." : exception.getReason();
        Result<Void> response = Result.failure(String.valueOf(exception.getStatusCode().value()), message);
        return ResponseEntity.status(exception.getStatusCode()).body(response);
    }

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<Result<Void>> handleAppException(AppException exception, HttpServletRequest request) {
        HttpStatus status = exception.getStatus();
        log.warn("业务异常，code={}, status={}", exception.getCode(), status.value());
        Result<Void> response = Result.failure(exception.getCode(), exception.getMessage());
        return ResponseEntity.status(status).body(response);
    }

    /**
     * 兜底处理未捕获异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception, HttpServletRequest request) {
        log.error("系统异常，exceptionType={}", exception.getClass().getSimpleName());
        Result<Void> response = Result.failure("SYSTEM_ERROR", "系统异常，请稍后重试");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
