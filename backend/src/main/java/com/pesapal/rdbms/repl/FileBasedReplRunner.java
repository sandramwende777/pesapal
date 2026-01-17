package com.pesapal.rdbms.repl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pesapal.rdbms.service.FileBasedSqlParserService;
import com.pesapal.rdbms.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Interactive REPL for the file-based RDBMS.
 * 
 * This provides a TRUE command-line interface similar to mysql, psql, or sqlite3.
 * All operations go through our file-based storage, NOT through JPA/H2.
 * 
 * To run: java -jar rdbms.jar --repl.enabled=true
 */
@Component
@ConditionalOnProperty(name = "repl.enabled", havingValue = "true")
@Order(10)  // Run after data initialization
@RequiredArgsConstructor
@Slf4j
public class FileBasedReplRunner implements CommandLineRunner {
    
    private final FileBasedSqlParserService sqlParserService;
    private final ObjectMapper objectMapper;
    
    private static final String BANNER = """
            
            ╔═══════════════════════════════════════════════════════════════════╗
            ║        File-Based RDBMS - Interactive SQL Interface               ║
            ║                        Version 2.0                                ║
            ║                                                                   ║
            ║  This RDBMS uses CUSTOM FILE STORAGE (not JPA/H2):                ║
            ║  - Schemas: data/schemas/*.schema.json                            ║
            ║  - Data:    data/tables/*.dat (page-based binary format)          ║
            ║  - Indexes: In-memory hash indexes for fast lookups               ║
            ╚═══════════════════════════════════════════════════════════════════╝
            
            Type SQL commands to interact with the database.
            Commands: SHOW TABLES, DESCRIBE <table>, CREATE TABLE, INSERT, SELECT, UPDATE, DELETE
            Type 'help' for examples, 'quit' or 'exit' to leave.
            
            """;
    
    private static final String HELP_TEXT = """
            
            ═══════════════════════════════════════════════════════════════════════
                                        SQL Examples
            ═══════════════════════════════════════════════════════════════════════
            
            -- Show all tables
            SHOW TABLES;
            
            -- Show index statistics
            SHOW INDEXES;
            
            -- Describe table structure
            DESCRIBE products;
            
            -- Create a new table
            CREATE TABLE employees (
                id INTEGER,
                name VARCHAR(100),
                email VARCHAR(200),
                salary DECIMAL,
                active BOOLEAN,
                PRIMARY KEY (id),
                UNIQUE (email)
            );
            
            -- Insert data
            INSERT INTO employees (id, name, email, salary, active)
            VALUES (1, 'John Doe', 'john@example.com', 75000.00, true);
            
            -- Select with WHERE clause (uses indexes when available!)
            SELECT name, salary FROM employees WHERE active = true;
            SELECT * FROM products WHERE category_id = 1;
            
            -- Select with ORDER BY (ascending or descending)
            SELECT * FROM products ORDER BY price DESC;
            SELECT * FROM products WHERE category_id = 1 ORDER BY name ASC;
            
            -- Select with LIMIT and OFFSET
            SELECT * FROM products ORDER BY price DESC LIMIT 5;
            SELECT * FROM products ORDER BY price LIMIT 10 OFFSET 5;
            
            -- EXPLAIN - show query execution plan (is index used?)
            EXPLAIN SELECT * FROM products WHERE category_id = 1;
            EXPLAIN SELECT * FROM products WHERE price > 100;
            
            -- Update data
            UPDATE employees SET salary = 80000.00 WHERE id = 1;
            
            -- Delete data
            DELETE FROM employees WHERE id = 1;
            
            -- Drop table
            DROP TABLE employees;
            
            -- Join tables (uses hash join or index nested loop)
            SELECT * FROM products INNER JOIN categories
            ON products.category_id = categories.id;
            
            ═══════════════════════════════════════════════════════════════════════
            
            """;
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println(BANNER);
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder multiLineQuery = new StringBuilder();
        boolean inMultiLine = false;
        
        while (true) {
            // Print prompt
            if (inMultiLine) {
                System.out.print("    -> ");
            } else {
                System.out.print("rdbms> ");
            }
            System.out.flush();
            
            String line = reader.readLine();
            
            // Handle EOF (Ctrl+D)
            if (line == null) {
                System.out.println("\nGoodbye!");
                System.exit(0);
            }
            
            line = line.trim();
            
            // Handle empty input
            if (line.isEmpty() && !inMultiLine) {
                continue;
            }
            
            // Handle special commands
            String lowerLine = line.toLowerCase();
            if (!inMultiLine) {
                if (lowerLine.equals("quit") || lowerLine.equals("exit") || lowerLine.equals("\\q")) {
                    System.out.println("Goodbye!");
                    System.exit(0);
                }
                
                if (lowerLine.equals("help") || lowerLine.equals("\\h") || lowerLine.equals("?")) {
                    System.out.println(HELP_TEXT);
                    continue;
                }
                
                if (lowerLine.equals("clear") || lowerLine.equals("\\c")) {
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                    continue;
                }
            }
            
            // Build query
            multiLineQuery.append(line).append(" ");
            
            // Check if query is complete
            String query = multiLineQuery.toString().trim();
            if (query.endsWith(";")) {
                inMultiLine = false;
                multiLineQuery = new StringBuilder();
                
                query = query.substring(0, query.length() - 1).trim();
                
                if (!query.isEmpty()) {
                    executeAndPrint(query);
                }
            } else {
                inMultiLine = true;
            }
        }
    }
    
    private void executeAndPrint(String sql) {
        try {
            long startTime = System.currentTimeMillis();
            Object result = sqlParserService.executeSql(sql);
            long duration = System.currentTimeMillis() - startTime;
            
            formatAndPrintResult(result, duration);
            
        } catch (IllegalArgumentException e) {
            System.out.println("\nError: " + e.getMessage());
            System.out.println();
        } catch (Exception e) {
            System.out.println("\nError: " + e.getMessage());
            log.error("REPL execution error", e);
            System.out.println();
        }
    }
    
    @SuppressWarnings("unchecked")
    private void formatAndPrintResult(Object result, long duration) {
        System.out.println();
        
        if (result == null) {
            System.out.println("Query executed successfully.");
        } else if (result instanceof TableSchema schema) {
            printTableDescription(schema);
        } else if (result instanceof Row row) {
            System.out.println("1 row inserted. (rowId=" + row.getRowId() + ")");
        } else if (result instanceof List<?> list) {
            if (list.isEmpty()) {
                System.out.println("Empty set (0 rows)");
            } else {
                Object first = list.get(0);
                if (first instanceof TableSchema) {
                    printTablesList((List<TableSchema>) list);
                } else if (first instanceof Map) {
                    printResultSet((List<Map<String, Object>>) list);
                } else {
                    printAsJson(result);
                }
            }
        } else if (result instanceof Integer count) {
            System.out.println(count + " row(s) affected.");
        } else if (result instanceof String msg) {
            System.out.println(msg);
        } else {
            printAsJson(result);
        }
        
        System.out.printf("Time: %d ms%n%n", duration);
    }
    
    private void printTablesList(List<TableSchema> tables) {
        if (tables.isEmpty()) {
            System.out.println("No tables found.");
            return;
        }
        
        int maxNameLen = "Table Name".length();
        int maxColsLen = "Columns".length();
        int maxRowsLen = "Rows".length();
        
        for (TableSchema t : tables) {
            maxNameLen = Math.max(maxNameLen, t.getTableName().length());
            maxColsLen = Math.max(maxColsLen, String.valueOf(t.getColumns().size()).length());
            maxRowsLen = Math.max(maxRowsLen, String.valueOf(t.getRowCount()).length());
        }
        
        String format = "| %-" + maxNameLen + "s | %" + maxColsLen + "s | %" + maxRowsLen + "s |%n";
        String separator = "+" + "-".repeat(maxNameLen + 2) + "+" + "-".repeat(maxColsLen + 2) + "+" + "-".repeat(maxRowsLen + 2) + "+";
        
        System.out.println(separator);
        System.out.printf(format, "Table Name", "Columns", "Rows");
        System.out.println(separator);
        
        for (TableSchema t : tables) {
            System.out.printf(format, t.getTableName(), t.getColumns().size(), t.getRowCount());
        }
        
        System.out.println(separator);
        System.out.println(tables.size() + " table(s) in database");
    }
    
    private void printTableDescription(TableSchema schema) {
        System.out.println("Table: " + schema.getTableName());
        System.out.println("Rows: " + schema.getRowCount());
        System.out.println();
        
        int maxNameLen = "Column".length();
        int maxTypeLen = "Type".length();
        int maxNullLen = "Nullable".length();
        int maxKeyLen = "Key".length();
        
        for (ColumnSchema col : schema.getColumns()) {
            maxNameLen = Math.max(maxNameLen, col.getName().length());
            String typeStr = formatDataType(col);
            maxTypeLen = Math.max(maxTypeLen, typeStr.length());
        }
        
        String format = "| %-" + maxNameLen + "s | %-" + maxTypeLen + "s | %-" + maxNullLen + "s | %-" + maxKeyLen + "s |%n";
        String separator = "+" + "-".repeat(maxNameLen + 2) + "+" + "-".repeat(maxTypeLen + 2) + "+" + "-".repeat(maxNullLen + 2) + "+" + "-".repeat(maxKeyLen + 2) + "+";
        
        System.out.println(separator);
        System.out.printf(format, "Column", "Type", "Nullable", "Key");
        System.out.println(separator);
        
        for (ColumnSchema col : schema.getColumns()) {
            String keyInfo = getKeyInfo(schema, col.getName());
            System.out.printf(format,
                    col.getName(),
                    formatDataType(col),
                    col.isNullable() ? "YES" : "NO",
                    keyInfo
            );
        }
        
        System.out.println(separator);
        System.out.println(schema.getColumns().size() + " column(s)");
        
        // Print indexes
        if (!schema.getIndexes().isEmpty()) {
            System.out.println();
            System.out.println("Indexes:");
            for (IndexSchema idx : schema.getIndexes()) {
                System.out.printf("  - %s on %s%s%n",
                        idx.getIndexName(),
                        idx.getColumnName(),
                        idx.isUnique() ? " (UNIQUE)" : ""
                );
            }
        }
    }
    
    private String formatDataType(ColumnSchema col) {
        String type = col.getDataType().toString();
        if (col.getMaxLength() != null && col.getMaxLength() > 0) {
            type += "(" + col.getMaxLength() + ")";
        }
        return type;
    }
    
    private String getKeyInfo(TableSchema schema, String columnName) {
        for (KeySchema key : schema.getKeys()) {
            if (key.getColumnName().equals(columnName)) {
                return key.getKeyType() == KeyType.PRIMARY ? "PRI" : "UNI";
            }
        }
        return "";
    }
    
    private void printResultSet(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            System.out.println("Empty set (0 rows)");
            return;
        }
        
        List<String> columns = rows.get(0).keySet().stream().sorted().collect(Collectors.toList());
        
        Map<String, Integer> widths = columns.stream()
                .collect(Collectors.toMap(
                        col -> col,
                        col -> Math.max(col.length(),
                                rows.stream()
                                        .map(row -> String.valueOf(row.get(col)))
                                        .mapToInt(String::length)
                                        .max()
                                        .orElse(4))
                ));
        
        StringBuilder formatBuilder = new StringBuilder("|");
        StringBuilder separatorBuilder = new StringBuilder("+");
        
        for (String col : columns) {
            int width = widths.get(col);
            formatBuilder.append(" %-").append(width).append("s |");
            separatorBuilder.append("-".repeat(width + 2)).append("+");
        }
        formatBuilder.append("%n");
        
        String format = formatBuilder.toString();
        String separator = separatorBuilder.toString();
        
        System.out.println(separator);
        System.out.printf(format, columns.toArray());
        System.out.println(separator);
        
        for (Map<String, Object> row : rows) {
            Object[] values = columns.stream()
                    .map(col -> {
                        Object val = row.get(col);
                        return val == null ? "NULL" : String.valueOf(val);
                    })
                    .toArray();
            System.out.printf(format, values);
        }
        
        System.out.println(separator);
        System.out.println(rows.size() + " row(s) returned");
    }
    
    private void printAsJson(Object result) {
        try {
            ObjectMapper prettyMapper = objectMapper.copy();
            prettyMapper.enable(SerializationFeature.INDENT_OUTPUT);
            System.out.println(prettyMapper.writeValueAsString(result));
        } catch (Exception e) {
            System.out.println(result.toString());
        }
    }
}
