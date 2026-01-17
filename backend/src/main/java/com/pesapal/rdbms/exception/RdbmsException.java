package com.pesapal.rdbms.exception;

/**
 * Base exception for all RDBMS-related errors.
 * 
 * <p>This exception hierarchy provides structured error handling for the RDBMS,
 * allowing clients to catch specific error types and respond appropriately.</p>
 * 
 * @author Pesapal RDBMS Team
 * @version 2.1
 */
public class RdbmsException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    /**
     * Error codes for categorizing RDBMS exceptions.
     */
    public enum ErrorCode {
        // Table errors
        TABLE_NOT_FOUND("T001", "Table not found"),
        TABLE_ALREADY_EXISTS("T002", "Table already exists"),
        
        // Column errors
        COLUMN_NOT_FOUND("C001", "Column not found"),
        INVALID_COLUMN_TYPE("C002", "Invalid column data type"),
        
        // Constraint errors
        PRIMARY_KEY_VIOLATION("K001", "Primary key constraint violation"),
        UNIQUE_KEY_VIOLATION("K002", "Unique key constraint violation"),
        NULL_CONSTRAINT_VIOLATION("K003", "NULL constraint violation"),
        
        // Query errors
        INVALID_SQL_SYNTAX("Q001", "Invalid SQL syntax"),
        INVALID_WHERE_CLAUSE("Q002", "Invalid WHERE clause"),
        INVALID_VALUE("Q003", "Invalid value for column type"),
        
        // Storage errors
        STORAGE_READ_ERROR("S001", "Failed to read from storage"),
        STORAGE_WRITE_ERROR("S002", "Failed to write to storage"),
        PAGE_FULL("S003", "Page is full, cannot insert row"),
        
        // Index errors
        INDEX_NOT_FOUND("I001", "Index not found"),
        INDEX_ALREADY_EXISTS("I002", "Index already exists"),
        
        // General errors
        INTERNAL_ERROR("G001", "Internal RDBMS error"),
        UNSUPPORTED_OPERATION("G002", "Operation not supported");
        
        private final String code;
        private final String defaultMessage;
        
        ErrorCode(String code, String defaultMessage) {
            this.code = code;
            this.defaultMessage = defaultMessage;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDefaultMessage() {
            return defaultMessage;
        }
    }
    
    public RdbmsException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
    
    public RdbmsException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public RdbmsException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s: %s", 
                errorCode.getCode(), 
                errorCode.name(), 
                getMessage());
    }
}
