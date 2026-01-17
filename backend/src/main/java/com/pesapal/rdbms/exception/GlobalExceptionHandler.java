package com.pesapal.rdbms.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for REST API endpoints.
 * 
 * <p>Converts exceptions to standardized JSON error responses with
 * appropriate HTTP status codes.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(TableNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTableNotFound(TableNotFoundException ex) {
        log.warn("Table not found: {}", ex.getTableName());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex);
    }
    
    @ExceptionHandler(InvalidSqlException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSql(InvalidSqlException ex) {
        log.warn("Invalid SQL: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex);
    }
    
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorageError(StorageException ex) {
        log.error("Storage error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }
    
    @ExceptionHandler(RdbmsException.class)
    public ResponseEntity<Map<String, Object>> handleRdbmsException(RdbmsException ex) {
        log.error("RDBMS error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
    
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, RdbmsException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("errorCode", ex.getErrorCode().getCode());
        body.put("errorType", ex.getErrorCode().name());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(status).body(body);
    }
}
