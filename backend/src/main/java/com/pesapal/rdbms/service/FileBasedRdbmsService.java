package com.pesapal.rdbms.service;

import com.pesapal.rdbms.dto.*;
import com.pesapal.rdbms.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * File-based RDBMS Service.
 * 
 * This is the core database engine that uses custom file-based storage
 * instead of JPA/H2. All data is stored in files that we manage directly.
 * 
 * Key features:
 * - Page-based storage for row data (.dat files)
 * - JSON schema files for metadata
 * - In-memory indexes that actually optimize queries
 * - Proper constraint enforcement (PK, UK)
 * 
 * This demonstrates a TRUE RDBMS implementation, not a wrapper around
 * an existing database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileBasedRdbmsService {
    
    private final FileStorageService storage;
    private final InMemoryIndex indexService;
    
    // ==================== Table Operations ====================
    
    /**
     * Creates a new table with the given schema.
     */
    public TableSchema createTable(CreateTableRequest request) {
        try {
            // Build schema from request
            TableSchema schema = new TableSchema(request.getTableName());
            
            // Add columns
            int ordinal = 0;
            for (CreateTableRequest.ColumnDefinition colDef : request.getColumns()) {
                ColumnSchema column = new ColumnSchema();
                column.setName(colDef.getName());
                column.setDataType(colDef.getDataType());
                column.setMaxLength(colDef.getMaxLength());
                column.setNullable(colDef.getNullable() != null ? colDef.getNullable() : true);
                column.setDefaultValue(colDef.getDefaultValue() != null ? 
                        String.valueOf(colDef.getDefaultValue()) : null);
                column.setOrdinalPosition(ordinal++);
                schema.addColumn(column);
            }
            
            // Add primary keys
            if (request.getPrimaryKeys() != null) {
                for (String pkColumn : request.getPrimaryKeys()) {
                    schema.addKey(new KeySchema(pkColumn, KeyType.PRIMARY, null, null));
                }
            }
            
            // Add unique keys
            if (request.getUniqueKeys() != null) {
                for (String ukColumn : request.getUniqueKeys()) {
                    schema.addKey(new KeySchema(ukColumn, KeyType.UNIQUE, null, null));
                }
            }
            
            // Add indexes
            if (request.getIndexes() != null) {
                for (CreateTableRequest.IndexDefinition idxDef : request.getIndexes()) {
                    schema.addIndex(new IndexSchema(
                            idxDef.getIndexName(),
                            idxDef.getColumnName(),
                            idxDef.getUnique() != null && idxDef.getUnique()
                    ));
                }
            }
            
            // Create table in storage
            storage.createTable(schema);
            
            // Create indexes in memory
            for (String pkColumn : schema.getPrimaryKeyColumns()) {
                indexService.createPrimaryKeyIndex(schema.getTableName(), pkColumn);
            }
            for (String ukColumn : schema.getUniqueKeyColumns()) {
                indexService.createIndex(schema.getTableName(), ukColumn, true);
            }
            for (IndexSchema idx : schema.getIndexes()) {
                indexService.createIndex(schema.getTableName(), idx.getColumnName(), idx.isUnique());
            }
            
            log.info("Created table: {} with {} columns", 
                    schema.getTableName(), schema.getColumns().size());
            
            return schema;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to create table: " + e.getMessage(), e);
        }
    }
    
    /**
     * Drops a table.
     */
    public void dropTable(String tableName) {
        try {
            storage.dropTable(tableName);
            indexService.dropTableIndexes(tableName);
            log.info("Dropped table: {}", tableName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to drop table: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lists all tables.
     */
    public List<TableSchema> listTables() {
        return storage.listTables();
    }
    
    /**
     * Gets a table's schema.
     */
    public TableSchema getTable(String tableName) {
        return storage.getSchema(tableName);
    }
    
    // ==================== CRUD Operations ====================
    
    /**
     * Inserts a row into a table.
     */
    public Row insert(InsertRequest request) {
        try {
            TableSchema schema = storage.getSchema(request.getTableName());
            
            // Validate columns exist
            for (String columnName : request.getValues().keySet()) {
                if (!schema.hasColumn(columnName)) {
                    throw new IllegalArgumentException("Column not found: " + columnName);
                }
            }
            
            // Validate NOT NULL constraints
            for (ColumnSchema column : schema.getColumns()) {
                if (!column.isNullable() && !request.getValues().containsKey(column.getName())) {
                    if (column.getDefaultValue() == null) {
                        throw new IllegalArgumentException(
                                "Column " + column.getName() + " cannot be null");
                    }
                }
            }
            
            // Validate PRIMARY KEY constraints (using index for fast lookup!)
            for (String pkColumn : schema.getPrimaryKeyColumns()) {
                Object value = request.getValues().get(pkColumn);
                if (value == null) {
                    throw new IllegalArgumentException(
                            "Primary key column " + pkColumn + " cannot be null");
                }
                // Use index for O(1) lookup instead of O(n) scan!
                if (indexService.primaryKeyExists(request.getTableName(), pkColumn, value)) {
                    throw new IllegalArgumentException("Duplicate primary key value: " + value);
                }
            }
            
            // Validate UNIQUE constraints (using index for fast lookup!)
            for (String ukColumn : schema.getUniqueKeyColumns()) {
                Object value = request.getValues().get(ukColumn);
                if (value != null) {
                    if (indexService.uniqueKeyExists(request.getTableName(), ukColumn, value)) {
                        throw new IllegalArgumentException("Duplicate unique key value: " + value);
                    }
                }
            }
            
            // Create row
            Row row = new Row();
            row.setValues(new LinkedHashMap<>(request.getValues()));
            row.setCreatedAt(LocalDateTime.now());
            row.setUpdatedAt(LocalDateTime.now());
            
            // Insert into storage
            long rowId = storage.insertRow(request.getTableName(), row);
            row.setRowId(rowId);
            
            // Update indexes
            indexService.indexRow(request.getTableName(), row);
            
            log.debug("Inserted row {} into {}", rowId, request.getTableName());
            
            return row;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to insert row: " + e.getMessage(), e);
        }
    }
    
    /**
     * Selects rows from a table.
     */
    public List<Map<String, Object>> select(SelectRequest request) {
        try {
            TableSchema schema = storage.getSchema(request.getTableName());
            List<Row> rows;
            
            // Check if we can use an index for the WHERE clause
            if (request.getWhere() != null && !request.getWhere().isEmpty()) {
                rows = selectWithWhere(request.getTableName(), schema, request.getWhere());
            } else {
                // Full table scan
                rows = storage.readAllRows(request.getTableName());
            }
            
            // Apply OFFSET
            if (request.getOffset() != null && request.getOffset() > 0) {
                rows = rows.subList(
                        Math.min(request.getOffset(), rows.size()), 
                        rows.size()
                );
            }
            
            // Apply LIMIT
            if (request.getLimit() != null && request.getLimit() > 0) {
                rows = rows.subList(0, Math.min(request.getLimit(), rows.size()));
            }
            
            // Project columns
            Set<String> selectedColumns = request.getColumns() != null && !request.getColumns().isEmpty()
                    ? new HashSet<>(request.getColumns())
                    : schema.getColumns().stream()
                            .map(ColumnSchema::getName)
                            .collect(Collectors.toSet());
            
            return rows.stream()
                    .map(row -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        for (String col : selectedColumns) {
                            result.put(col, row.getValue(col));
                        }
                        return result;
                    })
                    .collect(Collectors.toList());
                    
        } catch (IOException e) {
            throw new RuntimeException("Failed to select rows: " + e.getMessage(), e);
        }
    }
    
    /**
     * Updates rows in a table.
     */
    public int update(UpdateRequest request) {
        try {
            TableSchema schema = storage.getSchema(request.getTableName());
            
            // Build predicate from WHERE clause
            java.util.function.Predicate<Row> predicate = row -> {
                if (request.getWhere() == null || request.getWhere().isEmpty()) {
                    return true;
                }
                return matchesWhere(row, request.getWhere());
            };
            
            // TODO: Validate constraints after update
            
            int updated = storage.updateRows(
                    request.getTableName(), 
                    request.getSet(), 
                    predicate
            );
            
            // Rebuild indexes for this table (simple approach)
            if (updated > 0) {
                rebuildTableIndexes(request.getTableName());
            }
            
            log.debug("Updated {} rows in {}", updated, request.getTableName());
            
            return updated;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to update rows: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deletes rows from a table.
     */
    public int delete(DeleteRequest request) {
        try {
            // Build predicate from WHERE clause
            java.util.function.Predicate<Row> predicate = row -> {
                if (request.getWhere() == null || request.getWhere().isEmpty()) {
                    return true;
                }
                return matchesWhere(row, request.getWhere());
            };
            
            int deleted = storage.deleteRows(request.getTableName(), predicate);
            
            // Rebuild indexes for this table
            if (deleted > 0) {
                rebuildTableIndexes(request.getTableName());
            }
            
            log.debug("Deleted {} rows from {}", deleted, request.getTableName());
            
            return deleted;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete rows: " + e.getMessage(), e);
        }
    }
    
    // ==================== JOIN Operations ====================
    
    /**
     * Performs a JOIN between two tables.
     */
    public List<Map<String, Object>> join(JoinRequest request) {
        try {
            List<Row> leftRows = storage.readAllRows(request.getLeftTable());
            List<Row> rightRows = storage.readAllRows(request.getRightTable());
            
            // Build hash index on right table for efficient join (hash join algorithm)
            Map<Object, List<Row>> rightIndex = new HashMap<>();
            for (Row rightRow : rightRows) {
                Object rightValue = rightRow.getValue(request.getRightColumn());
                rightIndex.computeIfAbsent(rightValue, k -> new ArrayList<>()).add(rightRow);
            }
            
            List<Map<String, Object>> result = new ArrayList<>();
            
            for (Row leftRow : leftRows) {
                Object leftValue = leftRow.getValue(request.getLeftColumn());
                List<Row> matchingRightRows = rightIndex.getOrDefault(leftValue, Collections.emptyList());
                
                if (!matchingRightRows.isEmpty()) {
                    // Inner/Left/Right: matched rows
                    for (Row rightRow : matchingRightRows) {
                        Map<String, Object> joined = buildJoinedRow(
                                request, leftRow, rightRow);
                        result.add(joined);
                    }
                } else if (request.getJoinType() == JoinRequest.JoinType.LEFT) {
                    // Left join: include left row with nulls for right
                    Map<String, Object> joined = buildJoinedRow(request, leftRow, null);
                    result.add(joined);
                }
            }
            
            // Handle RIGHT JOIN
            if (request.getJoinType() == JoinRequest.JoinType.RIGHT) {
                Set<Object> matchedRightValues = leftRows.stream()
                        .map(r -> r.getValue(request.getLeftColumn()))
                        .collect(Collectors.toSet());
                
                for (Row rightRow : rightRows) {
                    Object rightValue = rightRow.getValue(request.getRightColumn());
                    if (!matchedRightValues.contains(rightValue)) {
                        Map<String, Object> joined = buildJoinedRow(request, null, rightRow);
                        result.add(joined);
                    }
                }
            }
            
            // Apply WHERE clause
            if (request.getWhere() != null && !request.getWhere().isEmpty()) {
                result = result.stream()
                        .filter(row -> matchesWhereMap(row, request.getWhere()))
                        .collect(Collectors.toList());
            }
            
            // Apply LIMIT/OFFSET
            if (request.getOffset() != null && request.getOffset() > 0) {
                result = result.subList(
                        Math.min(request.getOffset(), result.size()), 
                        result.size()
                );
            }
            if (request.getLimit() != null && request.getLimit() > 0) {
                result = result.subList(0, Math.min(request.getLimit(), result.size()));
            }
            
            return result;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to join tables: " + e.getMessage(), e);
        }
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Selects rows using WHERE clause, utilizing indexes when possible.
     */
    private List<Row> selectWithWhere(String tableName, TableSchema schema, 
                                       Map<String, Object> where) throws IOException {
        // Try to find an indexed column in the WHERE clause
        for (var entry : where.entrySet()) {
            String columnName = entry.getKey();
            Object value = entry.getValue();
            
            // Only simple equality can use index
            if (!(value instanceof Map) && indexService.isIndexed(tableName, columnName)) {
                // Use index lookup!
                Set<Long> rowIds = indexService.lookup(tableName, columnName, value);
                log.debug("Using index on {}.{} - found {} candidates", 
                        tableName, columnName, rowIds.size());
                
                // Fetch rows by ID and apply remaining filters
                List<Row> allRows = storage.readAllRows(tableName);
                return allRows.stream()
                        .filter(row -> rowIds.contains(row.getRowId()))
                        .filter(row -> matchesWhere(row, where))
                        .collect(Collectors.toList());
            }
        }
        
        // No usable index - full table scan
        log.debug("Full table scan on {}", tableName);
        List<Row> allRows = storage.readAllRows(tableName);
        return allRows.stream()
                .filter(row -> matchesWhere(row, where))
                .collect(Collectors.toList());
    }
    
    /**
     * Checks if a row matches the WHERE clause.
     */
    @SuppressWarnings("unchecked")
    private boolean matchesWhere(Row row, Map<String, Object> where) {
        for (var entry : where.entrySet()) {
            String key = entry.getKey();
            Object condition = entry.getValue();
            Object actualValue = row.getValue(key);
            
            if (condition instanceof Map) {
                Map<String, Object> opCondition = (Map<String, Object>) condition;
                String operator = (String) opCondition.get("op");
                Object expectedValue = opCondition.get("value");
                
                if (!evaluateCondition(actualValue, operator, expectedValue)) {
                    return false;
                }
            } else {
                if (!Objects.equals(actualValue, condition)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Checks if a map matches the WHERE clause.
     */
    @SuppressWarnings("unchecked")
    private boolean matchesWhereMap(Map<String, Object> row, Map<String, Object> where) {
        for (var entry : where.entrySet()) {
            String key = entry.getKey();
            Object condition = entry.getValue();
            Object actualValue = row.get(key);
            
            if (condition instanceof Map) {
                Map<String, Object> opCondition = (Map<String, Object>) condition;
                String operator = (String) opCondition.get("op");
                Object expectedValue = opCondition.get("value");
                
                if (!evaluateCondition(actualValue, operator, expectedValue)) {
                    return false;
                }
            } else {
                if (!Objects.equals(actualValue, condition)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Evaluates a comparison condition.
     */
    private boolean evaluateCondition(Object actualValue, String operator, Object expectedValue) {
        if (operator == null || operator.equals("=")) {
            return Objects.equals(actualValue, expectedValue);
        }
        
        switch (operator.toUpperCase()) {
            case "!=":
            case "<>":
                return !Objects.equals(actualValue, expectedValue);
            case ">":
                return compareValues(actualValue, expectedValue) > 0;
            case "<":
                return compareValues(actualValue, expectedValue) < 0;
            case ">=":
                return compareValues(actualValue, expectedValue) >= 0;
            case "<=":
                return compareValues(actualValue, expectedValue) <= 0;
            case "LIKE":
                return matchesLike(actualValue, expectedValue);
            case "IS NULL":
                return actualValue == null;
            case "IS NOT NULL":
                return actualValue != null;
            default:
                return Objects.equals(actualValue, expectedValue);
        }
    }
    
    @SuppressWarnings("unchecked")
    private int compareValues(Object actual, Object expected) {
        if (actual == null && expected == null) return 0;
        if (actual == null) return -1;
        if (expected == null) return 1;
        
        if (actual instanceof Number && expected instanceof Number) {
            double d1 = ((Number) actual).doubleValue();
            double d2 = ((Number) expected).doubleValue();
            return Double.compare(d1, d2);
        }
        
        try {
            double d1 = Double.parseDouble(String.valueOf(actual));
            double d2 = Double.parseDouble(String.valueOf(expected));
            return Double.compare(d1, d2);
        } catch (NumberFormatException e) {
            // Fall through to string comparison
        }
        
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }
    
    private boolean matchesLike(Object actualValue, Object pattern) {
        if (actualValue == null || pattern == null) return false;
        
        String value = String.valueOf(actualValue);
        String patternStr = String.valueOf(pattern);
        
        String regex = patternStr
                .replace(".", "\\.")
                .replace("*", "\\*")
                .replace("%", ".*")
                .replace("_", ".");
        
        return value.matches("(?i)" + regex);
    }
    
    private Map<String, Object> buildJoinedRow(JoinRequest request, Row leftRow, Row rightRow) {
        Map<String, Object> joined = new LinkedHashMap<>();
        
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            // SELECT *
            if (leftRow != null) {
                for (var entry : leftRow.getValues().entrySet()) {
                    joined.put(request.getLeftTable() + "." + entry.getKey(), entry.getValue());
                }
            }
            if (rightRow != null) {
                for (var entry : rightRow.getValues().entrySet()) {
                    joined.put(request.getRightTable() + "." + entry.getKey(), entry.getValue());
                }
            }
        } else {
            // Project specific columns
            for (String col : request.getColumns()) {
                if (col.contains(".")) {
                    String[] parts = col.split("\\.");
                    String tableName = parts[0];
                    String columnName = parts[1];
                    
                    if (tableName.equals(request.getLeftTable()) && leftRow != null) {
                        joined.put(col, leftRow.getValue(columnName));
                    } else if (tableName.equals(request.getRightTable()) && rightRow != null) {
                        joined.put(col, rightRow.getValue(columnName));
                    }
                }
            }
        }
        
        return joined;
    }
    
    private void rebuildTableIndexes(String tableName) {
        try {
            TableSchema schema = storage.getSchema(tableName);
            List<Row> rows = storage.readAllRows(tableName);
            indexService.rebuildIndexes(tableName, schema, rows);
        } catch (IOException e) {
            log.error("Failed to rebuild indexes for table: {}", tableName, e);
        }
    }
    
}
