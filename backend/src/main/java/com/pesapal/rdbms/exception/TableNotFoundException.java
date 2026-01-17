package com.pesapal.rdbms.exception;

/**
 * Exception thrown when a referenced table does not exist.
 */
public class TableNotFoundException extends RdbmsException {
    
    private final String tableName;
    
    public TableNotFoundException(String tableName) {
        super(ErrorCode.TABLE_NOT_FOUND, 
              String.format("Table '%s' does not exist", tableName));
        this.tableName = tableName;
    }
    
    public String getTableName() {
        return tableName;
    }
}
