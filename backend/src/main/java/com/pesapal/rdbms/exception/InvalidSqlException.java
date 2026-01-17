package com.pesapal.rdbms.exception;

/**
 * Exception thrown when SQL syntax is invalid or cannot be parsed.
 */
public class InvalidSqlException extends RdbmsException {
    
    private final String sql;
    
    public InvalidSqlException(String message, String sql) {
        super(ErrorCode.INVALID_SQL_SYNTAX, message);
        this.sql = sql;
    }
    
    public InvalidSqlException(String message) {
        super(ErrorCode.INVALID_SQL_SYNTAX, message);
        this.sql = null;
    }
    
    public String getSql() {
        return sql;
    }
}
