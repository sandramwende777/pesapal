package com.pesapal.rdbms.util;

import com.pesapal.rdbms.config.RdbmsConstants;
import com.pesapal.rdbms.exception.InvalidSqlException;
import com.pesapal.rdbms.exception.RdbmsException;
import com.pesapal.rdbms.storage.DataType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Utility class for input validation.
 * 
 * <p>Provides validation methods for SQL input, column values, and table names.</p>
 */
public final class ValidationUtils {
    
    /** Pattern for valid identifier names (table, column names) */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    
    /** Maximum identifier length */
    private static final int MAX_IDENTIFIER_LENGTH = 128;
    
    private ValidationUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Validates that an identifier (table/column name) is valid.
     *
     * @param identifier the identifier to validate
     * @param type description of what's being validated (for error messages)
     * @throws InvalidSqlException if the identifier is invalid
     */
    public static void validateIdentifier(String identifier, String type) {
        Objects.requireNonNull(identifier, type + " cannot be null");
        
        if (identifier.isBlank()) {
            throw new InvalidSqlException(type + " cannot be empty");
        }
        
        if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
            throw new InvalidSqlException(
                    String.format("%s '%s' exceeds maximum length of %d characters",
                            type, identifier, MAX_IDENTIFIER_LENGTH));
        }
        
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new InvalidSqlException(
                    String.format("%s '%s' contains invalid characters. " +
                            "Must start with a letter or underscore, " +
                            "followed by letters, numbers, or underscores.", type, identifier));
        }
    }
    
    /**
     * Validates that a table name is valid.
     */
    public static void validateTableName(String tableName) {
        validateIdentifier(tableName, "Table name");
    }
    
    /**
     * Validates that a column name is valid.
     */
    public static void validateColumnName(String columnName) {
        validateIdentifier(columnName, "Column name");
    }
    
    /**
     * Validates and converts a value to the appropriate type for a column.
     *
     * @param value the value to validate
     * @param dataType the expected data type
     * @param columnName the column name (for error messages)
     * @return the converted value
     * @throws RdbmsException if the value cannot be converted
     */
    public static Object validateAndConvertValue(Object value, DataType dataType, String columnName) {
        if (value == null) {
            return null;
        }
        
        try {
            return switch (dataType) {
                case INTEGER -> convertToInteger(value);
                case BIGINT -> convertToLong(value);
                case DECIMAL -> convertToDecimal(value);
                case BOOLEAN -> convertToBoolean(value);
                case DATE -> convertToDate(value);
                case TIMESTAMP -> convertToTimestamp(value);
                case VARCHAR, TEXT -> value.toString();
            };
        } catch (Exception e) {
            throw new RdbmsException(
                    RdbmsException.ErrorCode.INVALID_VALUE,
                    String.format("Invalid value '%s' for column '%s' of type %s: %s",
                            value, columnName, dataType, e.getMessage()));
        }
    }
    
    private static Integer convertToInteger(Object value) {
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString().trim());
    }
    
    private static Long convertToLong(Object value) {
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString().trim());
    }
    
    private static BigDecimal convertToDecimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return new BigDecimal(value.toString().trim());
    }
    
    private static Boolean convertToBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        String str = value.toString().trim().toLowerCase();
        return str.equals("true") || str.equals("1") || str.equals("yes");
    }
    
    private static LocalDate convertToDate(Object value) {
        if (value instanceof LocalDate) return (LocalDate) value;
        if (value instanceof LocalDateTime) return ((LocalDateTime) value).toLocalDate();
        return LocalDate.parse(value.toString().trim());
    }
    
    private static LocalDateTime convertToTimestamp(Object value) {
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        if (value instanceof LocalDate) return ((LocalDate) value).atStartOfDay();
        return LocalDateTime.parse(value.toString().trim());
    }
    
    /**
     * Validates that a string length is within bounds.
     */
    public static void validateStringLength(String value, int maxLength, String columnName) {
        if (value != null && value.length() > maxLength) {
            throw new RdbmsException(
                    RdbmsException.ErrorCode.INVALID_VALUE,
                    String.format("Value for column '%s' exceeds maximum length of %d characters",
                            columnName, maxLength));
        }
    }
    
    /**
     * Validates that a required parameter is not null or empty.
     */
    public static void requireNonEmpty(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new InvalidSqlException(paramName + " is required and cannot be empty");
        }
    }
    
    /**
     * Validates query limits.
     */
    public static int validateLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return RdbmsConstants.DEFAULT_QUERY_LIMIT;
        }
        return Math.min(limit, RdbmsConstants.MAX_QUERY_LIMIT);
    }
    
    /**
     * Validates query offset.
     */
    public static int validateOffset(Integer offset) {
        if (offset == null || offset < 0) {
            return RdbmsConstants.DEFAULT_OFFSET;
        }
        return offset;
    }
}
