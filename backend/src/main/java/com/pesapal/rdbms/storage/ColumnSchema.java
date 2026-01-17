package com.pesapal.rdbms.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a column definition in a table schema.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnSchema {
    
    private String name;
    private DataType dataType;
    private Integer maxLength;      // For VARCHAR
    private Integer precision;      // For DECIMAL
    private Integer scale;          // For DECIMAL
    private boolean nullable = true;
    private String defaultValue;
    private int ordinalPosition;    // Column order in table
    
    public ColumnSchema(String name, DataType dataType) {
        this.name = name;
        this.dataType = dataType;
    }
    
    public ColumnSchema(String name, DataType dataType, Integer maxLength, boolean nullable) {
        this.name = name;
        this.dataType = dataType;
        this.maxLength = maxLength;
        this.nullable = nullable;
    }
    
    /**
     * Returns the size in bytes for fixed-size types.
     * For variable types, returns -1.
     */
    @JsonIgnore
    public int getFixedSize() {
        if (dataType == null) return -1;
        return switch (dataType) {
            case INTEGER -> 4;
            case BIGINT -> 8;
            case BOOLEAN -> 1;
            case DECIMAL -> 16;  // Store as two longs (unscaled value + scale)
            case DATE -> 8;      // Store as epoch days
            case TIMESTAMP -> 8; // Store as epoch millis
            case VARCHAR, TEXT -> -1;  // Variable size
        };
    }
    
    @JsonIgnore
    public boolean isVariableLength() {
        return dataType == DataType.VARCHAR || dataType == DataType.TEXT;
    }
}
