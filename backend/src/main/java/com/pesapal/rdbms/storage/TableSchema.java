package com.pesapal.rdbms.storage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the schema (metadata) of a table.
 * This is stored as JSON in the schemas/ directory.
 * 
 * Unlike JPA entities, this is a pure POJO that we serialize/deserialize ourselves.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableSchema {
    
    private String tableName;
    private List<ColumnSchema> columns = new ArrayList<>();
    private List<KeySchema> keys = new ArrayList<>();
    private List<IndexSchema> indexes = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Statistics for query optimization
    private long rowCount = 0;
    private long nextRowId = 1;  // Auto-increment for internal row IDs
    
    public TableSchema(String tableName) {
        this.tableName = tableName;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public void addColumn(ColumnSchema column) {
        columns.add(column);
        updatedAt = LocalDateTime.now();
    }
    
    public void addKey(KeySchema key) {
        keys.add(key);
        updatedAt = LocalDateTime.now();
    }
    
    public void addIndex(IndexSchema index) {
        indexes.add(index);
        updatedAt = LocalDateTime.now();
    }
    
    public ColumnSchema getColumn(String columnName) {
        return columns.stream()
                .filter(c -> c.getName().equals(columnName))
                .findFirst()
                .orElse(null);
    }
    
    public boolean hasColumn(String columnName) {
        return columns.stream().anyMatch(c -> c.getName().equals(columnName));
    }
    
    public List<String> getPrimaryKeyColumns() {
        return keys.stream()
                .filter(k -> k.getKeyType() == KeyType.PRIMARY)
                .map(KeySchema::getColumnName)
                .toList();
    }
    
    public List<String> getUniqueKeyColumns() {
        return keys.stream()
                .filter(k -> k.getKeyType() == KeyType.UNIQUE)
                .map(KeySchema::getColumnName)
                .toList();
    }
    
    public boolean hasIndex(String columnName) {
        return indexes.stream().anyMatch(i -> i.getColumnName().equals(columnName));
    }
    
    public long getNextRowId() {
        return nextRowId++;
    }
    
    public void incrementRowCount() {
        rowCount++;
        updatedAt = LocalDateTime.now();
    }
    
    public void decrementRowCount() {
        if (rowCount > 0) rowCount--;
        updatedAt = LocalDateTime.now();
    }
}
