package com.assetmanagement.shared.web;

import com.assetmanagement.security.TraceIdFilter;
import com.assetmanagement.shared.api.ApiResponse;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(toStatus(errorCode))
                .body(ApiResponse.failure(
                        errorCode.code(),
                        exception.getMessage(),
                        null,
                        TraceIdFilter.traceId(request)
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> handleMethodValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR.code(),
                details.isEmpty() ? "Request validation failed" : String.join("; ", details.values()),
                details,
                TraceIdFilter.traceId(request)
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR.code(),
                exception.getMessage(),
                null,
                TraceIdFilter.traceId(request)
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.warn("Database constraint violation; traceId={}", TraceIdFilter.traceId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(
                ErrorCode.CONFLICT.code(),
                "The requested change conflicts with existing data",
                null,
                TraceIdFilter.traceId(request)
        ));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            OptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        log.warn("Optimistic lock conflict; traceId={}", TraceIdFilter.traceId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(
                ErrorCode.CONFLICT.code(),
                "数据刚被其他操作更新，请刷新后重试",
                null,
                TraceIdFilter.traceId(request)
        ));
    }

    @ExceptionHandler(QueryTimeoutException.class)
    ResponseEntity<ApiResponse<Void>> handleQueryTimeout(
            QueryTimeoutException exception,
            HttpServletRequest request
    ) {
        log.warn("Database query timed out; traceId={}", TraceIdFilter.traceId(request));
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(ApiResponse.failure(
                ErrorCode.INTERNAL_ERROR.code(),
                "数据库操作超时。流水过多时已改为后台分批清理，请稍后刷新进度",
                null,
                TraceIdFilter.traceId(request)
        ));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> handleDataAccess(
            DataAccessException exception,
            HttpServletRequest request
    ) {
        String raw = exception.getMostSpecificCause() == null
                ? ""
                : String.valueOf(exception.getMostSpecificCause().getMessage()).toLowerCase();
        if (raw.contains("timeout") || raw.contains("timed out")) {
            return handleQueryTimeout(new QueryTimeoutException(exception.getMessage(), exception), request);
        }
        return handleUnexpectedException(exception, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        String traceId = TraceIdFilter.traceId(request);
        log.error("Unhandled request error; traceId={}", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure(
                ErrorCode.INTERNAL_ERROR.code(),
                "操作失败，请稍后重试",
                null,
                traceId
        ));
    }

    private static HttpStatus toStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
