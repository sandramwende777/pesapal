package com.pesapal.rdbms.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index implementation for query optimization.
 * 
 * This provides actual index functionality (unlike the JPA version which only stored metadata).
 * Uses hash-based indexing for equality lookups.
 * 
 * Structure:
 *   tableName -> columnName -> value -> Set<rowId>
 * 
 * Example:
 *   products -> category_id -> 1 -> [1, 3, 5]  (products 1, 3, 5 have category_id=1)
 *   products -> category_id -> 2 -> [2, 4]    (products 2, 4 have category_id=2)
 */
@Component
@Slf4j
public class InMemoryIndex {
    
    // tableName -> columnName -> value -> Set<rowId>
    private final Map<String, Map<String, Map<Object, Set<Long>>>> indexes = new ConcurrentHashMap<>();
    
    // Track which columns are indexed per table
    private final Map<String, Set<String>> indexedColumns = new ConcurrentHashMap<>();
    
    // Primary key indexes (for fast uniqueness checks)
    // tableName -> columnName -> value -> rowId
    private final Map<String, Map<String, Map<Object, Long>>> primaryKeyIndexes = new ConcurrentHashMap<>();
    
    // Unique key indexes
    private final Map<String, Map<String, Map<Object, Long>>> uniqueKeyIndexes = new ConcurrentHashMap<>();
    
    /**
     * Creates an index on a column.
     */
    public void createIndex(String tableName, String columnName, boolean unique) {
        indexes.computeIfAbsent(tableName, k -> new ConcurrentHashMap<>())
               .computeIfAbsent(columnName, k -> new ConcurrentHashMap<>());
        
        indexedColumns.computeIfAbsent(tableName, k -> ConcurrentHashMap.newKeySet())
                      .add(columnName);
        
        if (unique) {
            uniqueKeyIndexes.computeIfAbsent(tableName, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(columnName, k -> new ConcurrentHashMap<>());
        }
        
        log.debug("Created index on {}.{} (unique={})", tableName, columnName, unique);
    }
    
    /**
     * Creates a primary key index.
     */
    public void createPrimaryKeyIndex(String tableName, String columnName) {
        primaryKeyIndexes.computeIfAbsent(tableName, k -> new ConcurrentHashMap<>())
                         .computeIfAbsent(columnName, k -> new ConcurrentHashMap<>());
        
        indexedColumns.computeIfAbsent(tableName, k -> ConcurrentHashMap.newKeySet())
                      .add(columnName);
        
        log.debug("Created primary key index on {}.{}", tableName, columnName);
    }
    
    /**
     * Adds a row to all applicable indexes.
     */
    public void indexRow(String tableName, Row row) {
        Map<String, Map<Object, Set<Long>>> tableIndexes = indexes.get(tableName);
        if (tableIndexes == null) return;
        
        for (var entry : row.getValues().entrySet()) {
            String columnName = entry.getKey();
            Object value = entry.getValue();
            
            Map<Object, Set<Long>> columnIndex = tableIndexes.get(columnName);
            if (columnIndex != null && value != null) {
                columnIndex.computeIfAbsent(value, k -> ConcurrentHashMap.newKeySet())
                           .add(row.getRowId());
            }
        }
        
        // Update primary key index
        Map<String, Map<Object, Long>> pkIndexes = primaryKeyIndexes.get(tableName);
        if (pkIndexes != null) {
            for (var pkEntry : pkIndexes.entrySet()) {
                String pkColumn = pkEntry.getKey();
                Object pkValue = row.getValue(pkColumn);
                if (pkValue != null) {
                    pkEntry.getValue().put(pkValue, row.getRowId());
                }
            }
        }
        
        // Update unique key index
        Map<String, Map<Object, Long>> ukIndexes = uniqueKeyIndexes.get(tableName);
        if (ukIndexes != null) {
            for (var ukEntry : ukIndexes.entrySet()) {
                String ukColumn = ukEntry.getKey();
                Object ukValue = row.getValue(ukColumn);
                if (ukValue != null) {
                    ukEntry.getValue().put(ukValue, row.getRowId());
                }
            }
        }
    }
    
    /**
     * Removes a row from all applicable indexes.
     */
    public void removeFromIndex(String tableName, Row row) {
        Map<String, Map<Object, Set<Long>>> tableIndexes = indexes.get(tableName);
        if (tableIndexes != null) {
            for (var entry : row.getValues().entrySet()) {
                String columnName = entry.getKey();
                Object value = entry.getValue();
                
                Map<Object, Set<Long>> columnIndex = tableIndexes.get(columnName);
                if (columnIndex != null && value != null) {
                    Set<Long> rowIds = columnIndex.get(value);
                    if (rowIds != null) {
                        rowIds.remove(row.getRowId());
                    }
                }
            }
        }
        
        // Remove from primary key index
        Map<String, Map<Object, Long>> pkIndexes = primaryKeyIndexes.get(tableName);
        if (pkIndexes != null) {
            for (var pkEntry : pkIndexes.entrySet()) {
                String pkColumn = pkEntry.getKey();
                Object pkValue = row.getValue(pkColumn);
                if (pkValue != null) {
                    pkEntry.getValue().remove(pkValue);
                }
            }
        }
        
        // Remove from unique key index
        Map<String, Map<Object, Long>> ukIndexes = uniqueKeyIndexes.get(tableName);
        if (ukIndexes != null) {
            for (var ukEntry : ukIndexes.entrySet()) {
                String ukColumn = ukEntry.getKey();
                Object ukValue = row.getValue(ukColumn);
                if (ukValue != null) {
                    ukEntry.getValue().remove(ukValue);
                }
            }
        }
    }
    
    /**
     * Looks up row IDs by indexed column value.
     * Returns null if column is not indexed (caller should do full scan).
     */
    public Set<Long> lookup(String tableName, String columnName, Object value) {
        Map<String, Map<Object, Set<Long>>> tableIndexes = indexes.get(tableName);
        if (tableIndexes == null) return null;
        
        Map<Object, Set<Long>> columnIndex = tableIndexes.get(columnName);
        if (columnIndex == null) return null;
        
        Set<Long> result = columnIndex.get(value);
        return result != null ? new HashSet<>(result) : Collections.emptySet();
    }
    
    /**
     * Checks if a column is indexed.
     */
    public boolean isIndexed(String tableName, String columnName) {
        Set<String> columns = indexedColumns.get(tableName);
        return columns != null && columns.contains(columnName);
    }
    
    /**
     * Checks if a primary key value already exists.
     */
    public boolean primaryKeyExists(String tableName, String columnName, Object value) {
        Map<String, Map<Object, Long>> pkIndexes = primaryKeyIndexes.get(tableName);
        if (pkIndexes == null) return false;
        
        Map<Object, Long> columnIndex = pkIndexes.get(columnName);
        if (columnIndex == null) return false;
        
        return columnIndex.containsKey(value);
    }
    
    /**
     * Checks if a unique key value already exists.
     */
    public boolean uniqueKeyExists(String tableName, String columnName, Object value) {
        // Check in unique key indexes
        Map<String, Map<Object, Long>> ukIndexes = uniqueKeyIndexes.get(tableName);
        if (ukIndexes != null) {
            Map<Object, Long> columnIndex = ukIndexes.get(columnName);
            if (columnIndex != null && columnIndex.containsKey(value)) {
                return true;
            }
        }
        
        // Also check primary keys
        return primaryKeyExists(tableName, columnName, value);
    }
    
    /**
     * Drops all indexes for a table.
     */
    public void dropTableIndexes(String tableName) {
        indexes.remove(tableName);
        indexedColumns.remove(tableName);
        primaryKeyIndexes.remove(tableName);
        uniqueKeyIndexes.remove(tableName);
        log.debug("Dropped all indexes for table: {}", tableName);
    }
    
    /**
     * Rebuilds indexes for a table from row data.
     */
    public void rebuildIndexes(String tableName, TableSchema schema, List<Row> rows) {
        // Clear existing indexes
        dropTableIndexes(tableName);
        
        // Create indexes based on schema
        for (String pkColumn : schema.getPrimaryKeyColumns()) {
            createPrimaryKeyIndex(tableName, pkColumn);
        }
        
        for (String ukColumn : schema.getUniqueKeyColumns()) {
            createIndex(tableName, ukColumn, true);
        }
        
        for (IndexSchema index : schema.getIndexes()) {
            createIndex(tableName, index.getColumnName(), index.isUnique());
        }
        
        // Index all rows
        for (Row row : rows) {
            if (row.isActive()) {
                indexRow(tableName, row);
            }
        }
        
        log.info("Rebuilt indexes for table {} with {} rows", tableName, rows.size());
    }
    
    /**
     * Gets statistics about indexes.
     */
    public Map<String, Object> getStats(String tableName) {
        Map<String, Object> stats = new HashMap<>();
        
        Set<String> columns = indexedColumns.get(tableName);
        stats.put("indexedColumns", columns != null ? columns.size() : 0);
        
        Map<String, Map<Object, Set<Long>>> tableIndexes = indexes.get(tableName);
        if (tableIndexes != null) {
            Map<String, Integer> indexSizes = new HashMap<>();
            for (var entry : tableIndexes.entrySet()) {
                indexSizes.put(entry.getKey(), entry.getValue().size());
            }
            stats.put("indexSizes", indexSizes);
        }
        
        return stats;
    }
}
