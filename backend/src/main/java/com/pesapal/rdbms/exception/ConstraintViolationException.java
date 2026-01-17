package com.pesapal.rdbms.exception;

/**
 * Exception thrown when a constraint (PRIMARY KEY, UNIQUE, NOT NULL) is violated.
 */
public class ConstraintViolationException extends RdbmsException {
    
    private final String tableName;
    private final String columnName;
    private final Object value;
    
    public ConstraintViolationException(ErrorCode errorCode, String tableName, 
                                         String columnName, Object value) {
        super(errorCode, String.format(
                "%s on table '%s', column '%s': value '%s' already exists or is invalid",
                errorCode.getDefaultMessage(), tableName, columnName, value));
        this.tableName = tableName;
        this.columnName = columnName;
        this.value = value;
    }
    
    public static ConstraintViolationException primaryKey(String tableName, 
                                                           String columnName, Object value) {
        return new ConstraintViolationException(
                ErrorCode.PRIMARY_KEY_VIOLATION, tableName, columnName, value);
    }
    
    public static ConstraintViolationException uniqueKey(String tableName, 
                                                          String columnName, Object value) {
        return new ConstraintViolationException(
                ErrorCode.UNIQUE_KEY_VIOLATION, tableName, columnName, value);
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public String getColumnName() {
        return columnName;
    }
    
    public Object getValue() {
        return value;
    }
}
