package com.pesapal.rdbms.storage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single row of data in a table.
 * 
 * Each row has:
 * - A unique internal ID (for referencing in indexes)
 * - Column values stored as a map
 * - Metadata (created/updated timestamps)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Row {
    
    private long rowId;                              // Internal row identifier
    private Map<String, Object> values = new LinkedHashMap<>();  // Column -> Value
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted = false;                 // Soft delete flag
    
    public Row(long rowId, Map<String, Object> values) {
        this.rowId = rowId;
        this.values = new LinkedHashMap<>(values);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public Object getValue(String columnName) {
        return values.get(columnName);
    }
    
    public void setValue(String columnName, Object value) {
        values.put(columnName, value);
        updatedAt = LocalDateTime.now();
    }
    
    public void markDeleted() {
        this.deleted = true;
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isActive() {
        return !deleted;
    }
}
