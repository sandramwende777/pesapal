package com.pesapal.rdbms.storage.index;

import com.pesapal.rdbms.storage.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all indexes for the RDBMS.
 * 
 * Responsibilities:
 * - Create/drop indexes
 * - Keep indexes in sync with data changes (insert/update/delete)
 * - Provide index lookup for query optimization
 * - Track index usage statistics
 */
@Component
@Slf4j
public class IndexManager {
    
    // tableName -> columnName -> BTreeIndex
    private final Map<String, Map<String, BTreeIndex>> indexes = new ConcurrentHashMap<>();
    
    // Primary key indexes (special handling for uniqueness)
    private final Map<String, Map<String, BTreeIndex>> primaryKeyIndexes = new ConcurrentHashMap<>();
    
    // Track which columns are indexed
    private final Map<String, Set<String>> indexedColumns = new ConcurrentHashMap<>();
    
    // Query statistics
    private long indexLookupsUsed = 0;
    private long fullTableScans = 0;
    
    /**
     * Creates a primary key index on a column.
     */
    public void createPrimaryKeyIndex(String tableName, String columnName) {
        BTreeIndex index = new BTreeIndex(
            "pk_" + tableName + "_" + columnName,
            tableName, 
            columnName, 
            true  // Primary keys are unique
        );
        
        primaryKeyIndexes.computeIfAbsent(tableName, k -> new ConcurrentHashMap<>())
                         .put(columnName, index);
        
        indexedColumns.computeIfAbsent(tableName, k -> ConcurrentHashMap.newKeySet())
                      .add(columnName);
        
        log.info("Created PRIMARY KEY index on {}.{}", tableName, columnName);
    }
    
    /**
     * Creates a regular index on a column.
     */
    public void createIndex(String tableName, String columnName, String indexName, boolean unique) {
        BTreeIndex index = new BTreeIndex(
            indexName,
            tableName,
            columnName,
            unique
        );
        
        indexes.computeIfAbsent(tableName, k -> new ConcurrentHashMap<>())
               .put(columnName, index);
        
        indexedColumns.computeIfAbsent(tableName, k -> ConcurrentHashMap.newKeySet())
                      .add(columnName);
        
        log.info("Created {} index '{}' on {}.{}", 
                 unique ? "UNIQUE" : "", indexName, tableName, columnName);
    }
    
    /**
     * Creates a unique index on a column.
     */
    public void createUniqueIndex(String tableName, String columnName) {
        createIndex(tableName, columnName, "uk_" + tableName + "_" + columnName, true);
    }
    
    /**
     * Drops all indexes for a table.
     */
    public void dropTableIndexes(String tableName) {
        indexes.remove(tableName);
        primaryKeyIndexes.remove(tableName);
        indexedColumns.remove(tableName);
        log.info("Dropped all indexes for table {}", tableName);
    }
    
    /**
     * Called when a new row is inserted.
     * Updates all relevant indexes.
     */
    public void onRowInserted(String tableName, Row row) {
        // Update primary key indexes
        Map<String, BTreeIndex> pkIndexes = primaryKeyIndexes.get(tableName);
        if (pkIndexes != null) {
            for (var entry : pkIndexes.entrySet()) {
                String column = entry.getKey();
                Object value = row.getValue(column);
                if (value != null) {
                    entry.getValue().insert(value, row.getRowId());
                }
            }
        }
        
        // Update regular indexes
        Map<String, BTreeIndex> tableIndexes = indexes.get(tableName);
        if (tableIndexes != null) {
            for (var entry : tableIndexes.entrySet()) {
                String column = entry.getKey();
                Object value = row.getValue(column);
                if (value != null) {
                    entry.getValue().insert(value, row.getRowId());
                }
            }
        }
    }
    
    /**
     * Called when a row is updated.
     * Updates all relevant indexes.
     */
    public void onRowUpdated(String tableName, Row oldRow, Row newRow) {
        // Update primary key indexes
        Map<String, BTreeIndex> pkIndexes = primaryKeyIndexes.get(tableName);
        if (pkIndexes != null) {
            for (var entry : pkIndexes.entrySet()) {
                String column = entry.getKey();
                Object oldValue = oldRow.getValue(column);
                Object newValue = newRow.getValue(column);
                if (!Objects.equals(oldValue, newValue)) {
                    if (oldValue != null) {
                        entry.getValue().delete(oldValue, oldRow.getRowId());
                    }
                    if (newValue != null) {
                        entry.getValue().insert(newValue, newRow.getRowId());
                    }
                }
            }
        }
        
        // Update regular indexes
        Map<String, BTreeIndex> tableIndexes = indexes.get(tableName);
        if (tableIndexes != null) {
            for (var entry : tableIndexes.entrySet()) {
                String column = entry.getKey();
                Object oldValue = oldRow.getValue(column);
                Object newValue = newRow.getValue(column);
                if (!Objects.equals(oldValue, newValue)) {
                    if (oldValue != null) {
                        entry.getValue().delete(oldValue, oldRow.getRowId());
                    }
                    if (newValue != null) {
                        entry.getValue().insert(newValue, newRow.getRowId());
                    }
                }
            }
        }
    }
    
    /**
     * Called when a row is deleted.
     * Updates all relevant indexes.
     */
    public void onRowDeleted(String tableName, Row row) {
        // Update primary key indexes
        Map<String, BTreeIndex> pkIndexes = primaryKeyIndexes.get(tableName);
        if (pkIndexes != null) {
            for (var entry : pkIndexes.entrySet()) {
                String column = entry.getKey();
                Object value = row.getValue(column);
                if (value != null) {
                    entry.getValue().delete(value, row.getRowId());
                }
            }
        }
        
        // Update regular indexes
        Map<String, BTreeIndex> tableIndexes = indexes.get(tableName);
        if (tableIndexes != null) {
            for (var entry : tableIndexes.entrySet()) {
                String column = entry.getKey();
                Object value = row.getValue(column);
                if (value != null) {
                    entry.getValue().delete(value, row.getRowId());
                }
            }
        }
    }
    
    /**
     * Checks if a column is indexed.
     */
    public boolean isIndexed(String tableName, String columnName) {
        Set<String> columns = indexedColumns.get(tableName);
        return columns != null && columns.contains(columnName);
    }
    
    /**
     * Gets the index for a column (if exists).
     */
    public BTreeIndex getIndex(String tableName, String columnName) {
        // Check primary key indexes first
        Map<String, BTreeIndex> pkIndexes = primaryKeyIndexes.get(tableName);
        if (pkIndexes != null && pkIndexes.containsKey(columnName)) {
            return pkIndexes.get(columnName);
        }
        
        // Then check regular indexes
        Map<String, BTreeIndex> tableIndexes = indexes.get(tableName);
        if (tableIndexes != null) {
            return tableIndexes.get(columnName);
        }
        
        return null;
    }
    
    /**
     * Find rows by exact key match using index.
     * Returns null if column is not indexed.
     */
    public Set<Long> findByKey(String tableName, String columnName, Object value) {
        BTreeIndex index = getIndex(tableName, columnName);
        if (index == null) {
            return null;  // Column not indexed
        }
        
        indexLookupsUsed++;
        return index.find(value);
    }
    
    /**
     * Find rows in a range using index.
     * Returns null if column is not indexed.
     */
    public Set<Long> findByRange(String tableName, String columnName, 
                                  Object min, Object max) {
        BTreeIndex index = getIndex(tableName, columnName);
        if (index == null) {
            return null;
        }
        
        indexLookupsUsed++;
        return index.findRange(min, max);
    }
    
    /**
     * Find rows greater than a value using index.
     */
    public Set<Long> findGreaterThan(String tableName, String columnName, 
                                      Object value, boolean inclusive) {
        BTreeIndex index = getIndex(tableName, columnName);
        if (index == null) {
            return null;
        }
        
        indexLookupsUsed++;
        return index.findGreaterThan(value, inclusive);
    }
    
    /**
     * Find rows less than a value using index.
     */
    public Set<Long> findLessThan(String tableName, String columnName, 
                                   Object value, boolean inclusive) {
        BTreeIndex index = getIndex(tableName, columnName);
        if (index == null) {
            return null;
        }
        
        indexLookupsUsed++;
        return index.findLessThan(value, inclusive);
    }
    
    /**
     * Check if a primary key value already exists.
     */
    public boolean primaryKeyExists(String tableName, String columnName, Object value) {
        Map<String, BTreeIndex> pkIndexes = primaryKeyIndexes.get(tableName);
        if (pkIndexes == null) return false;
        
        BTreeIndex index = pkIndexes.get(columnName);
        if (index == null) return false;
        
        return index.containsKey(value);
    }
    
    /**
     * Check if a unique key value already exists.
     */
    public boolean uniqueKeyExists(String tableName, String columnName, Object value) {
        // Check primary key
        if (primaryKeyExists(tableName, columnName, value)) {
            return true;
        }
        
        // Check regular indexes
        Map<String, BTreeIndex> tableIndexes = indexes.get(tableName);
        if (tableIndexes == null) return false;
        
        BTreeIndex index = tableIndexes.get(columnName);
        if (index == null || !index.isUnique()) return false;
        
        return index.containsKey(value);
    }
    
    /**
     * Rebuild all indexes for a table from row data.
     */
    public void rebuildIndexes(String tableName, TableSchema schema, List<Row> rows) {
        log.info("Rebuilding indexes for table {} with {} rows...", tableName, rows.size());
        long startTime = System.currentTimeMillis();
        
        // Clear existing indexes
        dropTableIndexes(tableName);
        
        // Create primary key indexes
        for (String pkColumn : schema.getPrimaryKeyColumns()) {
            createPrimaryKeyIndex(tableName, pkColumn);
        }
        
        // Create unique key indexes
        for (String ukColumn : schema.getUniqueKeyColumns()) {
            createUniqueIndex(tableName, ukColumn);
        }
        
        // Create regular indexes from schema
        for (IndexSchema indexSchema : schema.getIndexes()) {
            createIndex(tableName, indexSchema.getColumnName(), 
                       indexSchema.getIndexName(), indexSchema.isUnique());
        }
        
        // Index all rows
        for (Row row : rows) {
            if (row.isActive()) {
                onRowInserted(tableName, row);
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Rebuilt indexes for table {} in {} ms", tableName, duration);
    }
    
    /**
     * Record that a full table scan was performed.
     */
    public void recordFullTableScan() {
        fullTableScans++;
    }
    
    /**
     * Get index usage statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("indexLookupsUsed", indexLookupsUsed);
        stats.put("fullTableScans", fullTableScans);
        stats.put("indexedTablesCount", indexedColumns.size());
        
        // Per-table stats
        Map<String, Object> tableStats = new LinkedHashMap<>();
        for (String tableName : indexedColumns.keySet()) {
            Map<String, Object> tableInfo = new LinkedHashMap<>();
            tableInfo.put("indexedColumns", indexedColumns.get(tableName));
            
            List<Map<String, Object>> indexDetails = new ArrayList<>();
            
            Map<String, BTreeIndex> pkIdx = primaryKeyIndexes.get(tableName);
            if (pkIdx != null) {
                for (BTreeIndex idx : pkIdx.values()) {
                    indexDetails.add(idx.getStats());
                }
            }
            
            Map<String, BTreeIndex> regIdx = indexes.get(tableName);
            if (regIdx != null) {
                for (BTreeIndex idx : regIdx.values()) {
                    indexDetails.add(idx.getStats());
                }
            }
            
            tableInfo.put("indexes", indexDetails);
            tableStats.put(tableName, tableInfo);
        }
        stats.put("tables", tableStats);
        
        return stats;
    }
    
    /**
     * Get a list of all indexes for a table.
     */
    public List<String> getIndexNames(String tableName) {
        List<String> names = new ArrayList<>();
        
        Map<String, BTreeIndex> pkIdx = primaryKeyIndexes.get(tableName);
        if (pkIdx != null) {
            for (BTreeIndex idx : pkIdx.values()) {
                names.add(idx.getIndexName());
            }
        }
        
        Map<String, BTreeIndex> regIdx = indexes.get(tableName);
        if (regIdx != null) {
            for (BTreeIndex idx : regIdx.values()) {
                names.add(idx.getIndexName());
            }
        }
        
        return names;
    }
}
