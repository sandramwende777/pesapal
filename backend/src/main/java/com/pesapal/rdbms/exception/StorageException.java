package com.pesapal.rdbms.exception;

/**
 * Exception thrown when storage operations fail (read/write errors).
 */
public class StorageException extends RdbmsException {
    
    private final String tableName;
    
    public StorageException(ErrorCode errorCode, String tableName, String message) {
        super(errorCode, message);
        this.tableName = tableName;
    }
    
    public StorageException(ErrorCode errorCode, String tableName, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.tableName = tableName;
    }
    
    public static StorageException readError(String tableName, Throwable cause) {
        return new StorageException(
                ErrorCode.STORAGE_READ_ERROR, 
                tableName,
                String.format("Failed to read from table '%s': %s", tableName, cause.getMessage()),
                cause);
    }
    
    public static StorageException writeError(String tableName, Throwable cause) {
        return new StorageException(
                ErrorCode.STORAGE_WRITE_ERROR,
                tableName,
                String.format("Failed to write to table '%s': %s", tableName, cause.getMessage()),
                cause);
    }
    
    public String getTableName() {
        return tableName;
    }
}
