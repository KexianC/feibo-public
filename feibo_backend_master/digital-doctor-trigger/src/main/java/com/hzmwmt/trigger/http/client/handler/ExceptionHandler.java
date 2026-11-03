package com.hzmwmt.trigger.http.client.handler;

import com.hzmwmt.api.response.Response;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.exception.AuthorizationDataUnavailableException;
import com.hzmwmt.types.exception.RateLimitException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts controller exceptions to the existing business response envelope and
 * an HTTP status that accurately describes the failure.
 */
@RestControllerAdvice
@Slf4j
public class ExceptionHandler {

    private static final int MAX_CLIENT_MESSAGE_LENGTH = 512;

    private static final Set<String> UNAUTHENTICATED_CODES = Set.of(
            ResponseCode.TOKEN_EXPIRED.getCode(),
            ResponseCode.REFRESH_TOKEN_EXCEPTION.getCode(),
            ResponseCode.E0001.getCode(),
            ResponseCode.E0003.getCode(),
            ResponseCode.UNAUTHORIZED.getCode(),
            ResponseCode.MFA_REQUIRED.getCode()
    );

    private static final Set<String> FORBIDDEN_CODES = Set.of(
            ResponseCode.AUTHORIZATION_EXCEPTION.getCode(),
            ResponseCode.FORBIDDEN.getCode(),
            ResponseCode.E0006.getCode(),
            ResponseCode.E0007.getCode(),
            ResponseCode.E011.getCode()
    );

    private static final Set<String> CONFLICT_CODES = Set.of(
            ResponseCode.INDEX_EXCEPTION.getCode(),
            ResponseCode.UPDATE_ZERO.getCode(),
            ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode(),
            ResponseCode.NO_PAY_ORDER_EXCEPTION.getCode(),
            ResponseCode.RESOURCE_QUOTA_ANOMALY.getCode(),
            ResponseCode.E0004.getCode(),
            ResponseCode.E008.getCode(),
            ResponseCode.E009.getCode(),
            ResponseCode.E010.getCode()
    );

    private static final Set<String> INTERNAL_ERROR_CODES = Set.of(
            ResponseCode.UN_ERROR.getCode(),
            ResponseCode.RUNTIME_EXCEPTION.getCode(),
            ResponseCode.SERVICE_ERROR.getCode(),
            ResponseCode.MANAGE_ERROR.getCode(),
            ResponseCode.UPLOAD_EXCEPTION.getCode(),
            ResponseCode.DELETE_EXCEPTION.getCode(),
            ResponseCode.SYSTEM_ERROR.getCode(),
            "SYSTEM_ERROR",
            "DB_UPDATE_FAILED"
    );

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        String expectedType = exception.getRequiredType() == null
                ? "有效类型"
                : exception.getRequiredType().getSimpleName();
        String detail = String.format("参数'%s'的格式不正确，期望的类型是'%s'",
                exception.getName(), expectedType);
        log.warn("请求参数类型转换失败: parameter={}", exception.getName());
        return response(HttpStatus.BAD_REQUEST, ResponseCode.ILLEGAL_PARAMETER, detail);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception) {
        log.warn("请求方法不支持: method={}", exception.getMethod());
        return response(HttpStatus.METHOD_NOT_ALLOWED, ResponseCode.REQUEST_METHOD_NOT_SUPPORTED, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception) {
        log.warn("请求媒体类型不支持");
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ResponseCode.ILLEGAL_PARAMETER, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<Response<String>> handleNoResourceFound(
            Exception exception) {
        log.warn("请求资源不存在: type={}, httpStatus={}",
                exceptionType(exception), HttpStatus.NOT_FOUND.value());
        return response(HttpStatus.NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .map(ExceptionHandler::safeClientMessage)
                .orElse(ResponseCode.ILLEGAL_PARAMETER.getInfo());
        log.warn("请求参数绑定或校验失败");
        return response(HttpStatus.BAD_REQUEST,
                ResponseCode.ILLEGAL_PARAMETER.getCode(), message, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception) {
        String detail = String.format("必需的请求参数'%s'缺失或无效", exception.getParameterName());
        log.warn("请求参数缺失: parameter={}", exception.getParameterName());
        return response(HttpStatus.BAD_REQUEST, ResponseCode.ILLEGAL_PARAMETER, detail);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleServletRequestBinding(
            ServletRequestBindingException exception) {
        log.warn("请求参数绑定失败: type={}", exception.getClass().getSimpleName());
        return response(HttpStatus.BAD_REQUEST, ResponseCode.ILLEGAL_PARAMETER, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleUnreadableMessage(
            HttpMessageNotReadableException exception) {
        log.warn("请求体无法解析");
        return response(HttpStatus.BAD_REQUEST, ResponseCode.ILLEGAL_PARAMETER, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleMethodValidation(
            HandlerMethodValidationException exception) {
        log.warn("方法参数校验失败");
        return response(HttpStatus.BAD_REQUEST, ResponseCode.ILLEGAL_PARAMETER, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleConstraintViolation(
            ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        log.warn("请求参数约束校验失败");
        return response(HttpStatus.BAD_REQUEST, ResponseCode.ILLEGAL_PARAMETER,
                safeClientMessage(message));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleAuthentication(AuthenticationException exception) {
        log.warn("请求未通过身份认证: type={}", exception.getClass().getSimpleName());
        return response(HttpStatus.UNAUTHORIZED, ResponseCode.UNAUTHORIZED, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleAccessDenied(AccessDeniedException exception) {
        log.warn("请求被拒绝: type={}", exception.getClass().getSimpleName());
        return response(HttpStatus.FORBIDDEN, ResponseCode.FORBIDDEN, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleRateLimit(RateLimitException exception) {
        log.warn("请求触发服务端限流");
        return response(HttpStatus.TOO_MANY_REQUESTS,
                exception.getCode(), safeClientMessage(exception.getInfo()), null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class
    })
    public ResponseEntity<Response<String>> handleDataConflict(RuntimeException exception) {
        log.warn("数据写入冲突: type={}", exception.getClass().getSimpleName());
        return response(HttpStatus.CONFLICT, ResponseCode.INDEX_EXCEPTION, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        log.warn("上传内容超过大小限制");
        return response(HttpStatus.PAYLOAD_TOO_LARGE, ResponseCode.UPLOAD_EXCEPTION, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleResourceAccess(
            ResourceAccessException exception) {
        log.error("下游资源暂时不可用: type={}, code={}",
                exceptionType(exception), ResponseCode.INTERNET_EXCEPTION.getCode());
        return response(HttpStatus.SERVICE_UNAVAILABLE, ResponseCode.INTERNET_EXCEPTION, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleAuthorizationDataUnavailable(
            AuthorizationDataUnavailableException exception) {
        log.error("授权数据暂时不可用: type={}, code={}",
                exceptionType(exception), ResponseCode.SERVICE_ERROR.getCode());
        return response(HttpStatus.SERVICE_UNAVAILABLE, ResponseCode.SERVICE_ERROR, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleAppException(AppException exception) {
        HttpStatus status = statusFor(exception);
        String info = clientInfoFor(exception, status);
        String logCode = controlledLogCode(exception.getCode());
        if (status.is5xxServerError()) {
            log.error("业务处理触发服务端异常: type={}, code={}, httpStatus={}",
                    exceptionType(exception), logCode, status.value());
        } else {
            log.warn("业务请求失败: type={}, code={}, httpStatus={}",
                    exceptionType(exception), logCode, status.value());
        }
        return response(status, safeCode(exception.getCode(), status), info, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleRuntimeException(RuntimeException exception) {
        log.error("未处理的运行时异常: type={}, code={}, httpStatus={}",
                exceptionType(exception), ResponseCode.RUNTIME_EXCEPTION.getCode(),
                HttpStatus.INTERNAL_SERVER_ERROR.value());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ResponseCode.RUNTIME_EXCEPTION, null);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<Response<String>> handleException(Exception exception) {
        log.error("未处理的未知异常: type={}, code={}, httpStatus={}",
                exceptionType(exception), ResponseCode.UN_ERROR.getCode(),
                HttpStatus.INTERNAL_SERVER_ERROR.value());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ResponseCode.UN_ERROR, null);
    }

    private static HttpStatus statusFor(AppException exception) {
        String code = exception.getCode();
        if (code == null || code.isBlank()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (UNAUTHENTICATED_CODES.contains(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (FORBIDDEN_CODES.contains(code)) {
            return HttpStatus.FORBIDDEN;
        }
        if (CONFLICT_CODES.contains(code)) {
            return HttpStatus.CONFLICT;
        }
        if (ResponseCode.RESOURCE_NOT_FOUND.getCode().equals(code)
                || ResponseCode.E0005.getCode().equals(code)
                || "USER_NOT_FOUND".equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if (ResponseCode.REQUEST_METHOD_NOT_SUPPORTED.getCode().equals(code)) {
            return HttpStatus.METHOD_NOT_ALLOWED;
        }
        if (ResponseCode.HTTP_EXCEPTION.getCode().equals(code)
                || ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode().equals(code)) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (ResponseCode.INTERNET_EXCEPTION.getCode().equals(code)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (INTERNAL_ERROR_CODES.contains(code)
                || exception.getCause() != null && exception.getInfo() == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static String clientInfoFor(AppException exception, HttpStatus status) {
        ResponseCode canonical = responseCode(exception.getCode());
        if (status.is5xxServerError()) {
            return canonical == null
                    ? ResponseCode.SERVICE_ERROR.getInfo()
                    : canonical.getInfo();
        }
        if (status == HttpStatus.UNAUTHORIZED && canonical != null) {
            return canonical.getInfo();
        }
        if (exception.getInfo() != null && !exception.getInfo().isBlank()) {
            return safeClientMessage(exception.getInfo());
        }
        return canonical == null
                ? ResponseCode.ILLEGAL_PARAMETER.getInfo()
                : canonical.getInfo();
    }

    private static String safeCode(String code, HttpStatus status) {
        if (code != null && !code.isBlank()) {
            return code;
        }
        return status.is5xxServerError()
                ? ResponseCode.SERVICE_ERROR.getCode()
                : ResponseCode.ILLEGAL_PARAMETER.getCode();
    }

    private static ResponseCode responseCode(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(ResponseCode.values())
                .filter(candidate -> candidate.getCode().equals(code))
                .findFirst()
                .orElse(null);
    }

    private static String controlledLogCode(String code) {
        ResponseCode canonical = responseCode(code);
        return canonical == null ? "UNMAPPED" : canonical.getCode();
    }

    private static String exceptionType(Throwable exception) {
        if (exception == null) {
            return "Unknown";
        }
        String type = exception.getClass().getSimpleName();
        return type == null || type.isBlank() ? "Unknown" : type;
    }

    private static String safeClientMessage(String message) {
        if (message == null || message.isBlank()) {
            return ResponseCode.ILLEGAL_PARAMETER.getInfo();
        }
        String normalized = message.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").trim();
        return normalized.length() <= MAX_CLIENT_MESSAGE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_CLIENT_MESSAGE_LENGTH);
    }

    private static ResponseEntity<Response<String>> response(
            HttpStatus status, ResponseCode responseCode, String data) {
        return response(status, responseCode.getCode(), responseCode.getInfo(), data);
    }

    private static ResponseEntity<Response<String>> response(
            HttpStatus status, String code, String info, String data) {
        return ResponseEntity.status(status)
                .body(Response.<String>builder()
                        .code(code)
                        .info(info)
                        .data(data)
                        .build());
    }
}
