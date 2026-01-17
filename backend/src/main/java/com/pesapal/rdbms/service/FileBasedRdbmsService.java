package com.pesapal.rdbms.service;

import com.pesapal.rdbms.dto.*;
import com.pesapal.rdbms.storage.*;
import com.pesapal.rdbms.storage.index.IndexManager;
import com.pesapal.rdbms.storage.index.QueryExecution;
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
 * - B-Tree indexes that optimize queries (with range query support)
 * - Proper constraint enforcement (PK, UK)
 * - Query execution logging showing index usage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileBasedRdbmsService {
    
    private final FileStorageService storage;
    private final IndexManager indexManager;
    
    // Track last query execution for debugging
    private QueryExecution lastQueryExecution;
    
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
            
            // Create indexes using IndexManager
            for (String pkColumn : schema.getPrimaryKeyColumns()) {
                indexManager.createPrimaryKeyIndex(schema.getTableName(), pkColumn);
            }
            for (String ukColumn : schema.getUniqueKeyColumns()) {
                indexManager.createUniqueIndex(schema.getTableName(), ukColumn);
            }
            for (IndexSchema idx : schema.getIndexes()) {
                indexManager.createIndex(schema.getTableName(), idx.getColumnName(), 
                                         idx.getIndexName(), idx.isUnique());
            }
            
            log.info("Created table: {} with {} columns, {} indexes", 
                    schema.getTableName(), schema.getColumns().size(),
                    indexManager.getIndexNames(schema.getTableName()).size());
            
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
            indexManager.dropTableIndexes(tableName);
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
    
    /**
     * Gets index statistics.
     */
    public Map<String, Object> getIndexStats() {
        return indexManager.getStats();
    }
    
    /**
     * Gets the last query execution details.
     */
    public QueryExecution getLastQueryExecution() {
        return lastQueryExecution;
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
            
            // Validate PRIMARY KEY constraints (using B-Tree index for O(log n) lookup!)
            for (String pkColumn : schema.getPrimaryKeyColumns()) {
                Object value = request.getValues().get(pkColumn);
                if (value == null) {
                    throw new IllegalArgumentException(
                            "Primary key column " + pkColumn + " cannot be null");
                }
                if (indexManager.primaryKeyExists(request.getTableName(), pkColumn, value)) {
                    throw new IllegalArgumentException("Duplicate primary key value: " + value);
                }
            }
            
            // Validate UNIQUE constraints
            for (String ukColumn : schema.getUniqueKeyColumns()) {
                Object value = request.getValues().get(ukColumn);
                if (value != null) {
                    if (indexManager.uniqueKeyExists(request.getTableName(), ukColumn, value)) {
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
            indexManager.onRowInserted(request.getTableName(), row);
            
            log.debug("Inserted row {} into {}", rowId, request.getTableName());
            
            return row;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to insert row: " + e.getMessage(), e);
        }
    }
    
    /**
     * Selects rows from a table with index optimization.
     */
    public List<Map<String, Object>> select(SelectRequest request) {
        try {
            long startTime = System.currentTimeMillis();
            TableSchema schema = storage.getSchema(request.getTableName());
            List<Row> allRows = storage.readAllRows(request.getTableName());
            List<Row> filteredRows;
            
            // Build query execution info
            QueryExecution.QueryExecutionBuilder execBuilder = QueryExecution.builder()
                    .tableName(request.getTableName())
                    .queryType("SELECT")
                    .whereClause(request.getWhere())
                    .selectedColumns(request.getColumns())
                    .rowsScanned(allRows.size());
            
            // Check if we can use an index for the WHERE clause
            if (request.getWhere() != null && !request.getWhere().isEmpty()) {
                filteredRows = selectWithIndexOptimization(
                        request.getTableName(), allRows, request.getWhere(), execBuilder);
            } else {
                // Full table scan
                indexManager.recordFullTableScan();
                execBuilder.indexUsed(false);
                filteredRows = new ArrayList<>(allRows);
                log.info("SELECT on {}: Full table scan ({} rows)", 
                        request.getTableName(), allRows.size());
            }
            
            // Apply OFFSET
            if (request.getOffset() != null && request.getOffset() > 0) {
                filteredRows = filteredRows.subList(
                        Math.min(request.getOffset(), filteredRows.size()), 
                        filteredRows.size()
                );
            }
            
            // Apply LIMIT
            if (request.getLimit() != null && request.getLimit() > 0) {
                filteredRows = filteredRows.subList(0, Math.min(request.getLimit(), filteredRows.size()));
            }
            
            // Project columns
            Set<String> selectedColumns = request.getColumns() != null && !request.getColumns().isEmpty()
                    ? new HashSet<>(request.getColumns())
                    : schema.getColumns().stream()
                            .map(ColumnSchema::getName)
                            .collect(Collectors.toSet());
            
            List<Map<String, Object>> result = filteredRows.stream()
                    .map(row -> {
                        Map<String, Object> rowMap = new LinkedHashMap<>();
                        for (String col : selectedColumns) {
                            rowMap.put(col, row.getValue(col));
                        }
                        return rowMap;
                    })
                    .collect(Collectors.toList());
            
            // Record execution stats
            long duration = System.currentTimeMillis() - startTime;
            lastQueryExecution = execBuilder
                    .rowsReturned(result.size())
                    .executionTimeMs(duration)
                    .build();
            
            log.info(lastQueryExecution.toLogMessage());
            
            return result;
                    
        } catch (IOException e) {
            throw new RuntimeException("Failed to select rows: " + e.getMessage(), e);
        }
    }
    
    /**
     * Selects rows with index optimization.
     * Tries to use indexes for WHERE clause conditions.
     */
    @SuppressWarnings("unchecked")
    private List<Row> selectWithIndexOptimization(
            String tableName, 
            List<Row> allRows,
            Map<String, Object> where,
            QueryExecution.QueryExecutionBuilder execBuilder) {
        
        // Try each WHERE condition to find one that can use an index
        for (var entry : where.entrySet()) {
            String columnName = entry.getKey();
            Object condition = entry.getValue();
            
            // Check if this column is indexed
            if (!indexManager.isIndexed(tableName, columnName)) {
                continue;
            }
            
            Set<Long> matchingRowIds = null;
            String indexOperation = null;
            
            if (condition instanceof Map) {
                // Complex condition with operator
                Map<String, Object> opCondition = (Map<String, Object>) condition;
                String operator = (String) opCondition.get("op");
                Object value = opCondition.get("value");
                
                switch (operator.toUpperCase()) {
                    case "=":
                        matchingRowIds = indexManager.findByKey(tableName, columnName, value);
                        indexOperation = "EQUALITY_LOOKUP";
                        break;
                    case ">":
                        matchingRowIds = indexManager.findGreaterThan(tableName, columnName, value, false);
                        indexOperation = "RANGE_SCAN_GT";
                        break;
                    case ">=":
                        matchingRowIds = indexManager.findGreaterThan(tableName, columnName, value, true);
                        indexOperation = "RANGE_SCAN_GTE";
                        break;
                    case "<":
                        matchingRowIds = indexManager.findLessThan(tableName, columnName, value, false);
                        indexOperation = "RANGE_SCAN_LT";
                        break;
                    case "<=":
                        matchingRowIds = indexManager.findLessThan(tableName, columnName, value, true);
                        indexOperation = "RANGE_SCAN_LTE";
                        break;
                    // LIKE, IS NULL, etc. don't use index efficiently
                }
            } else {
                // Simple equality condition
                matchingRowIds = indexManager.findByKey(tableName, columnName, condition);
                indexOperation = "EQUALITY_LOOKUP";
            }
            
            if (matchingRowIds != null) {
                // Index was used!
                var index = indexManager.getIndex(tableName, columnName);
                execBuilder.indexUsed(true)
                          .indexName(index.getIndexName())
                          .indexColumn(columnName)
                          .indexOperation(indexOperation);
                
                log.info("SELECT on {}: Using index '{}' on column '{}' ({}) -> {} candidate rows",
                        tableName, index.getIndexName(), columnName, indexOperation, matchingRowIds.size());
                
                // Filter rows by matching IDs, then apply remaining conditions
                Set<Long> finalMatchingRowIds = matchingRowIds;
                return allRows.stream()
                        .filter(row -> finalMatchingRowIds.contains(row.getRowId()))
                        .filter(row -> matchesWhere(row, where))
                        .collect(Collectors.toList());
            }
        }
        
        // No usable index found - full table scan
        indexManager.recordFullTableScan();
        execBuilder.indexUsed(false);
        log.info("SELECT on {}: No suitable index, full table scan ({} rows)", 
                tableName, allRows.size());
        
        return allRows.stream()
                .filter(row -> matchesWhere(row, where))
                .collect(Collectors.toList());
    }
    
    /**
     * Updates rows in a table.
     */
    public int update(UpdateRequest request) {
        try {
            long startTime = System.currentTimeMillis();
            List<Row> allRows = storage.readAllRows(request.getTableName());
            
            // Find rows to update using index if possible
            List<Row> rowsToUpdate;
            if (request.getWhere() != null && !request.getWhere().isEmpty()) {
                QueryExecution.QueryExecutionBuilder execBuilder = QueryExecution.builder()
                        .tableName(request.getTableName())
                        .queryType("UPDATE")
                        .whereClause(request.getWhere())
                        .rowsScanned(allRows.size());
                
                rowsToUpdate = selectWithIndexOptimization(
                        request.getTableName(), allRows, request.getWhere(), execBuilder);
                
                lastQueryExecution = execBuilder.build();
            } else {
                rowsToUpdate = allRows;
            }
            
            // Update matching rows
            int updated = 0;
            for (Row row : rowsToUpdate) {
                Row oldRow = new Row(row.getRowId(), new LinkedHashMap<>(row.getValues()));
                
                for (var entry : request.getSet().entrySet()) {
                    row.setValue(entry.getKey(), entry.getValue());
                }
                
                // Update index
                indexManager.onRowUpdated(request.getTableName(), oldRow, row);
                updated++;
            }
            
            // Persist changes
            if (updated > 0) {
                java.util.function.Predicate<Row> predicate = row -> 
                    matchesWhere(row, request.getWhere() != null ? request.getWhere() : Collections.emptyMap());
                storage.updateRows(request.getTableName(), request.getSet(), predicate);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("UPDATE on {}: {} rows updated in {} ms", 
                    request.getTableName(), updated, duration);
            
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
            long startTime = System.currentTimeMillis();
            List<Row> allRows = storage.readAllRows(request.getTableName());
            
            // Find rows to delete using index if possible
            List<Row> rowsToDelete;
            if (request.getWhere() != null && !request.getWhere().isEmpty()) {
                QueryExecution.QueryExecutionBuilder execBuilder = QueryExecution.builder()
                        .tableName(request.getTableName())
                        .queryType("DELETE")
                        .whereClause(request.getWhere())
                        .rowsScanned(allRows.size());
                
                rowsToDelete = selectWithIndexOptimization(
                        request.getTableName(), allRows, request.getWhere(), execBuilder);
                
                lastQueryExecution = execBuilder.build();
            } else {
                rowsToDelete = allRows;
            }
            
            // Update indexes
            for (Row row : rowsToDelete) {
                indexManager.onRowDeleted(request.getTableName(), row);
            }
            
            // Delete from storage
            java.util.function.Predicate<Row> predicate = row -> 
                request.getWhere() == null || request.getWhere().isEmpty() || 
                matchesWhere(row, request.getWhere());
            
            int deleted = storage.deleteRows(request.getTableName(), predicate);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("DELETE on {}: {} rows deleted in {} ms", 
                    request.getTableName(), deleted, duration);
            
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
            long startTime = System.currentTimeMillis();
            List<Row> leftRows = storage.readAllRows(request.getLeftTable());
            List<Row> rightRows = storage.readAllRows(request.getRightTable());
            
            // Try to use index on right table's join column
            boolean usingIndex = indexManager.isIndexed(request.getRightTable(), request.getRightColumn());
            
            List<Map<String, Object>> result = new ArrayList<>();
            
            if (usingIndex) {
                // Index nested loop join
                log.info("JOIN: Using index on {}.{}", request.getRightTable(), request.getRightColumn());
                
                for (Row leftRow : leftRows) {
                    Object leftValue = leftRow.getValue(request.getLeftColumn());
                    Set<Long> matchingRightIds = indexManager.findByKey(
                            request.getRightTable(), request.getRightColumn(), leftValue);
                    
                    if (matchingRightIds != null && !matchingRightIds.isEmpty()) {
                        for (Row rightRow : rightRows) {
                            if (matchingRightIds.contains(rightRow.getRowId())) {
                                result.add(buildJoinedRow(request, leftRow, rightRow));
                            }
                        }
                    } else if (request.getJoinType() == JoinRequest.JoinType.LEFT) {
                        result.add(buildJoinedRow(request, leftRow, null));
                    }
                }
            } else {
                // Hash join (build hash table on right side)
                log.info("JOIN: Using hash join (no index on {}.{})", 
                        request.getRightTable(), request.getRightColumn());
                
                Map<Object, List<Row>> rightIndex = new HashMap<>();
                for (Row rightRow : rightRows) {
                    Object rightValue = rightRow.getValue(request.getRightColumn());
                    rightIndex.computeIfAbsent(rightValue, k -> new ArrayList<>()).add(rightRow);
                }
                
                for (Row leftRow : leftRows) {
                    Object leftValue = leftRow.getValue(request.getLeftColumn());
                    List<Row> matchingRightRows = rightIndex.getOrDefault(leftValue, Collections.emptyList());
                    
                    if (!matchingRightRows.isEmpty()) {
                        for (Row rightRow : matchingRightRows) {
                            result.add(buildJoinedRow(request, leftRow, rightRow));
                        }
                    } else if (request.getJoinType() == JoinRequest.JoinType.LEFT) {
                        result.add(buildJoinedRow(request, leftRow, null));
                    }
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
                        result.add(buildJoinedRow(request, null, rightRow));
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
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("JOIN {}.{} = {}.{}: {} rows in {} ms", 
                    request.getLeftTable(), request.getLeftColumn(),
                    request.getRightTable(), request.getRightColumn(),
                    result.size(), duration);
            
            return result;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to join tables: " + e.getMessage(), e);
        }
    }
    
    // ==================== Helper Methods ====================
    
    @SuppressWarnings("unchecked")
    private boolean matchesWhere(Row row, Map<String, Object> where) {
        if (where == null || where.isEmpty()) return true;
        
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
}
