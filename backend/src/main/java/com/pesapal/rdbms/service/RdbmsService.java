package com.pesapal.rdbms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pesapal.rdbms.dto.*;
import com.pesapal.rdbms.entity.*;
import com.pesapal.rdbms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core RDBMS Service: Implements the database management system operations.
 * 
 * ARCHITECTURE: Metadata-Based RDBMS
 * 
 * This service implements a simple RDBMS by storing metadata about user-created tables
 * in database entities (DatabaseTable, TableColumn, TableKey, TableIndex, TableRow).
 * 
 * Why Metadata Entities Instead of In-Memory?
 * 1. Persistence: Schema survives application restarts
 * 2. Queryable: Can query metadata (SHOW TABLES, DESCRIBE)
 * 3. Validation: Can validate constraints using stored metadata
 * 4. Demonstrates: Shows understanding of how databases store their own metadata
 * 
 * Flow:
 * - User creates table → Store metadata in DatabaseTable/TableColumn entities
 * - User inserts data → Store in TableRow entity (as JSON), validate using metadata
 * - User queries data → Retrieve TableRow entities, filter using metadata
 * 
 * The metadata entities (DatabaseTable, TableColumn, etc.) are the ENGINE.
 * The actual user data is stored in TableRow entities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RdbmsService {

    private final DatabaseTableRepository tableRepository;
    private final TableColumnRepository columnRepository;
    private final TableKeyRepository keyRepository;
    private final TableIndexRepository indexRepository;
    private final TableRowRepository rowRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new table by storing its metadata.
     * 
     * This method stores the table schema in metadata entities:
     * - DatabaseTable: Table name and metadata
     * - TableColumn: Column definitions (name, type, nullable, etc.)
     * - TableKey: Primary and unique key constraints
     * - TableIndex: Index definitions
     * 
     * The actual table doesn't exist as a JPA entity - it exists as metadata
     * that we use to validate and manage data stored in TableRow entities.
     */
    @Transactional
    public DatabaseTable createTable(CreateTableRequest request) {
        if (tableRepository.existsByTableName(request.getTableName())) {
            throw new IllegalArgumentException("Table " + request.getTableName() + " already exists");
        }

        // Store table metadata
        DatabaseTable table = new DatabaseTable();
        table.setTableName(request.getTableName());
        table = tableRepository.save(table);

        // Store column metadata - these define the table structure
        for (CreateTableRequest.ColumnDefinition colDef : request.getColumns()) {
            TableColumn column = new TableColumn();
            column.setTable(table);
            column.setColumnName(colDef.getName());
            column.setDataType(colDef.getDataType());
            column.setMaxLength(colDef.getMaxLength());
            column.setNullable(colDef.getNullable() != null ? colDef.getNullable() : true);
            // Convert defaultValue to String for storage (H2 TEXT column)
            Object defaultValue = colDef.getDefaultValue();
            column.setDefaultValue(defaultValue != null ? String.valueOf(defaultValue) : null);
            table.getColumns().add(column);
        }
        columnRepository.saveAll(table.getColumns());

        // Store primary key constraints - used for validation on insert/update
        if (request.getPrimaryKeys() != null) {
            for (String pkColumn : request.getPrimaryKeys()) {
                TableKey key = new TableKey();
                key.setTable(table);
                key.setColumnName(pkColumn);
                key.setKeyType(TableKey.KeyType.PRIMARY);
                table.getKeys().add(key);
            }
        }

        // Store unique key constraints - used for validation on insert/update
        if (request.getUniqueKeys() != null) {
            for (String ukColumn : request.getUniqueKeys()) {
                TableKey key = new TableKey();
                key.setTable(table);
                key.setColumnName(ukColumn);
                key.setKeyType(TableKey.KeyType.UNIQUE);
                table.getKeys().add(key);
            }
        }
        if (!table.getKeys().isEmpty()) {
            keyRepository.saveAll(table.getKeys());
        }

        // Store index definitions - used for query optimization metadata
        if (request.getIndexes() != null) {
            for (CreateTableRequest.IndexDefinition idxDef : request.getIndexes()) {
                TableIndex index = new TableIndex();
                index.setTable(table);
                index.setIndexName(idxDef.getIndexName());
                index.setColumnName(idxDef.getColumnName());
                index.setUnique(idxDef.getUnique());
                table.getIndexes().add(index);
            }
            indexRepository.saveAll(table.getIndexes());
        }

        return table;
    }

    /**
     * Inserts a row into a user-created table.
     * 
     * Process:
     * 1. Retrieve table metadata (DatabaseTable with TableColumns)
     * 2. Validate columns exist (using metadata)
     * 3. Validate constraints (primary keys, unique keys, nullable) using metadata
     * 4. Store row data as JSON in TableRow entity
     * 
     * The row data is stored as JSON because user tables have dynamic schemas.
     * We use the metadata (TableColumn entities) to validate the data structure.
     */
    @Transactional
    public TableRow insert(InsertRequest request) {
        // Get table metadata to validate against
        DatabaseTable table = tableRepository.findByTableName(request.getTableName())
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + request.getTableName()));

        // Validate columns exist using metadata
        Set<String> validColumns = table.getColumns().stream()
                .map(TableColumn::getColumnName)
                .collect(Collectors.toSet());

        for (String columnName : request.getValues().keySet()) {
            if (!validColumns.contains(columnName)) {
                throw new IllegalArgumentException("Column not found: " + columnName);
            }
        }

        // Validate constraints using stored metadata
        validatePrimaryKey(table, request.getValues());
        validateUniqueKeys(table, request.getValues());

        // Validate nullable constraints using metadata
        for (TableColumn column : table.getColumns()) {
            if (!column.getNullable() && !request.getValues().containsKey(column.getColumnName())) {
                if (column.getDefaultValue() == null) {
                    throw new IllegalArgumentException("Column " + column.getColumnName() + " is not nullable");
                }
            }
        }

        // Store row data as JSON (supports dynamic schemas)
        TableRow row = new TableRow();
        row.setTable(table);
        try {
            row.setRowData(objectMapper.writeValueAsString(request.getValues()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize row data", e);
        }

        return rowRepository.save(row);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> select(SelectRequest request) {
        DatabaseTable table = tableRepository.findByTableName(request.getTableName())
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + request.getTableName()));

        List<TableRow> rows = rowRepository.findByTable(table);

        // Apply WHERE clause
        if (request.getWhere() != null && !request.getWhere().isEmpty()) {
            rows = filterRows(rows, request.getWhere());
        }

        // Apply LIMIT and OFFSET
        if (request.getOffset() != null && request.getOffset() > 0) {
            rows = rows.subList(Math.min(request.getOffset(), rows.size()), rows.size());
        }
        if (request.getLimit() != null && request.getLimit() > 0) {
            rows = rows.subList(0, Math.min(request.getLimit(), rows.size()));
        }

        // Project columns
        Set<String> selectedColumns = request.getColumns() != null && !request.getColumns().isEmpty()
                ? new HashSet<>(request.getColumns())
                : table.getColumns().stream().map(TableColumn::getColumnName).collect(Collectors.toSet());

        return rows.stream()
                .map(row -> {
                    try {
                        Map<String, Object> rowData = objectMapper.readValue(
                                row.getRowData(),
                                new TypeReference<Map<String, Object>>() {}
                        );
                        Map<String, Object> result = new HashMap<>();
                        for (String col : selectedColumns) {
                            result.put(col, rowData.get(col));
                        }
                        return result;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to deserialize row data", e);
                    }
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public int update(UpdateRequest request) {
        DatabaseTable table = tableRepository.findByTableName(request.getTableName())
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + request.getTableName()));

        List<TableRow> rows = rowRepository.findByTable(table);

        // Apply WHERE clause
        if (request.getWhere() != null && !request.getWhere().isEmpty()) {
            rows = filterRows(rows, request.getWhere());
        }

        int updated = 0;
        for (TableRow row : rows) {
            try {
                Map<String, Object> rowData = objectMapper.readValue(
                        row.getRowData(),
                        new TypeReference<Map<String, Object>>() {}
                );

                // Update values
                for (Map.Entry<String, Object> entry : request.getSet().entrySet()) {
                    rowData.put(entry.getKey(), entry.getValue());
                }

                // Validate constraints
                validatePrimaryKey(table, rowData);
                validateUniqueKeys(table, rowData);

                row.setRowData(objectMapper.writeValueAsString(rowData));
                rowRepository.save(row);
                updated++;
            } catch (Exception e) {
                throw new RuntimeException("Failed to update row", e);
            }
        }

        return updated;
    }

    @Transactional
    public int delete(DeleteRequest request) {
        DatabaseTable table = tableRepository.findByTableName(request.getTableName())
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + request.getTableName()));

        List<TableRow> rows = rowRepository.findByTable(table);

        // Apply WHERE clause
        if (request.getWhere() != null && !request.getWhere().isEmpty()) {
            rows = filterRows(rows, request.getWhere());
        }

        rowRepository.deleteAll(rows);
        return rows.size();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> join(JoinRequest request) {
        DatabaseTable leftTable = tableRepository.findByTableName(request.getLeftTable())
                .orElseThrow(() -> new IllegalArgumentException("Left table not found: " + request.getLeftTable()));

        DatabaseTable rightTable = tableRepository.findByTableName(request.getRightTable())
                .orElseThrow(() -> new IllegalArgumentException("Right table not found: " + request.getRightTable()));

        List<TableRow> leftRows = rowRepository.findByTable(leftTable);
        List<TableRow> rightRows = rowRepository.findByTable(rightTable);

        List<Map<String, Object>> result = new ArrayList<>();

        for (TableRow leftRow : leftRows) {
            try {
                Map<String, Object> leftData = objectMapper.readValue(
                        leftRow.getRowData(),
                        new TypeReference<Map<String, Object>>() {}
                );

                Object leftValue = leftData.get(request.getLeftColumn());

                boolean matched = false;
                for (TableRow rightRow : rightRows) {
                    Map<String, Object> rightData = objectMapper.readValue(
                            rightRow.getRowData(),
                            new TypeReference<Map<String, Object>>() {}
                    );

                    Object rightValue = rightData.get(request.getRightColumn());

                    if (Objects.equals(leftValue, rightValue)) {
                        matched = true;
                        Map<String, Object> joined = new HashMap<>();
                        if (request.getColumns() == null || request.getColumns().isEmpty()) {
                            // SELECT *
                            leftData.forEach((k, v) -> joined.put(request.getLeftTable() + "." + k, v));
                            rightData.forEach((k, v) -> joined.put(request.getRightTable() + "." + k, v));
                        } else {
                            // Project specific columns
                            for (String col : request.getColumns()) {
                                if (col.contains(".")) {
                                    String[] parts = col.split("\\.");
                                    String tableName = parts[0];
                                    String columnName = parts[1];
                                    if (tableName.equals(request.getLeftTable())) {
                                        joined.put(col, leftData.get(columnName));
                                    } else if (tableName.equals(request.getRightTable())) {
                                        joined.put(col, rightData.get(columnName));
                                    }
                                }
                            }
                        }
                        result.add(joined);
                    }
                }

                // LEFT JOIN: include left row even if no match
                if (request.getJoinType() == JoinRequest.JoinType.LEFT && !matched) {
                    Map<String, Object> joined = new HashMap<>();
                    leftData.forEach((k, v) -> joined.put(request.getLeftTable() + "." + k, v));
                    result.add(joined);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to join rows", e);
            }
        }

        // Apply WHERE clause
        if (request.getWhere() != null && !request.getWhere().isEmpty()) {
            result = filterMaps(result, request.getWhere());
        }

        // Apply LIMIT and OFFSET
        if (request.getOffset() != null && request.getOffset() > 0) {
            result = result.subList(Math.min(request.getOffset(), result.size()), result.size());
        }
        if (request.getLimit() != null && request.getLimit() > 0) {
            result = result.subList(0, Math.min(request.getLimit(), result.size()));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<DatabaseTable> listTables() {
        return tableRepository.findAll();
    }

    @Transactional(readOnly = true)
    public DatabaseTable getTable(String tableName) {
        return tableRepository.findByTableName(tableName)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableName));
    }

    private List<TableRow> filterRows(List<TableRow> rows, Map<String, Object> where) {
        return rows.stream()
                .filter(row -> {
                    try {
                        Map<String, Object> rowData = objectMapper.readValue(
                                row.getRowData(),
                                new TypeReference<Map<String, Object>>() {}
                        );
                        return matchesWhere(rowData, where);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> filterMaps(List<Map<String, Object>> maps, Map<String, Object> where) {
        return maps.stream()
                .filter(map -> matchesWhere(map, where))
                .collect(Collectors.toList());
    }

    private boolean matchesWhere(Map<String, Object> rowData, Map<String, Object> where) {
        for (Map.Entry<String, Object> condition : where.entrySet()) {
            String key = condition.getKey();
            Object expectedValue = condition.getValue();

            // Handle table.column format
            Object actualValue;
            if (key.contains(".")) {
                String[] parts = key.split("\\.");
                actualValue = rowData.get(key); // Use full key
            } else {
                actualValue = rowData.get(key);
            }

            if (!Objects.equals(actualValue, expectedValue)) {
                return false;
            }
        }
        return true;
    }

    private void validatePrimaryKey(DatabaseTable table, Map<String, Object> rowData) {
        List<TableKey> primaryKeys = table.getKeys().stream()
                .filter(k -> k.getKeyType() == TableKey.KeyType.PRIMARY)
                .collect(Collectors.toList());

        if (!primaryKeys.isEmpty()) {
            for (TableKey pk : primaryKeys) {
                Object value = rowData.get(pk.getColumnName());
                if (value == null) {
                    throw new IllegalArgumentException("Primary key column " + pk.getColumnName() + " cannot be null");
                }

                // Check for duplicate primary key
                List<TableRow> existingRows = rowRepository.findByTable(table);
                for (TableRow existingRow : existingRows) {
                    try {
                        Map<String, Object> existingData = objectMapper.readValue(
                                existingRow.getRowData(),
                                new TypeReference<Map<String, Object>>() {}
                        );
                        if (Objects.equals(existingData.get(pk.getColumnName()), value)) {
                            throw new IllegalArgumentException("Duplicate primary key value: " + value);
                        }
                    } catch (Exception e) {
                        // Skip invalid rows
                    }
                }
            }
        }
    }

    private void validateUniqueKeys(DatabaseTable table, Map<String, Object> rowData) {
        List<TableKey> uniqueKeys = table.getKeys().stream()
                .filter(k -> k.getKeyType() == TableKey.KeyType.UNIQUE)
                .collect(Collectors.toList());

        for (TableKey uk : uniqueKeys) {
            Object value = rowData.get(uk.getColumnName());
            if (value != null) {
                // Check for duplicate unique key
                List<TableRow> existingRows = rowRepository.findByTable(table);
                for (TableRow existingRow : existingRows) {
                    try {
                        Map<String, Object> existingData = objectMapper.readValue(
                                existingRow.getRowData(),
                                new TypeReference<Map<String, Object>>() {}
                        );
                        if (Objects.equals(existingData.get(uk.getColumnName()), value)) {
                            throw new IllegalArgumentException("Duplicate unique key value: " + value);
                        }
                    } catch (Exception e) {
                        // Skip invalid rows
                    }
                }
            }
        }
    }
}
