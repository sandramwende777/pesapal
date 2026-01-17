package com.pesapal.rdbms.repl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pesapal.rdbms.entity.DatabaseTable;
import com.pesapal.rdbms.entity.TableColumn;
import com.pesapal.rdbms.entity.TableKey;
import com.pesapal.rdbms.service.SqlParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Interactive REPL (Read-Eval-Print-Loop) for the Simple RDBMS.
 * 
 * This provides a command-line interface where users can type SQL queries
 * and see results immediately, similar to mysql, psql, or sqlite3 CLIs.
 * 
 * To run in REPL mode, start the application with:
 *   java -jar rdbms.jar --repl.enabled=true
 * 
 * Or set in application.properties:
 *   repl.enabled=true
 */
@Component
@ConditionalOnProperty(name = "repl.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ReplRunner implements CommandLineRunner {

    private final SqlParserService sqlParserService;
    private final ObjectMapper objectMapper;

    private static final String BANNER = """
            
            ╔═══════════════════════════════════════════════════════════════╗
            ║           Simple RDBMS - Interactive SQL Interface            ║
            ║                        Version 1.0                            ║
            ╚═══════════════════════════════════════════════════════════════╝
            
            Type SQL commands to interact with the database.
            Commands: SHOW TABLES, DESCRIBE <table>, CREATE TABLE, INSERT, SELECT, UPDATE, DELETE
            Type 'help' for examples, 'quit' or 'exit' to leave.
            
            """;

    private static final String HELP_TEXT = """
            
            ═══════════════════════════════════════════════════════════════════
                                    SQL Examples
            ═══════════════════════════════════════════════════════════════════
            
            -- Show all tables
            SHOW TABLES;
            
            -- Describe table structure
            DESCRIBE products;
            
            -- Create a new table
            CREATE TABLE employees (
                id INTEGER,
                name VARCHAR(100),
                email VARCHAR(200),
                salary DECIMAL(10,2),
                active BOOLEAN,
                PRIMARY KEY (id),
                UNIQUE (email)
            );
            
            -- Insert data
            INSERT INTO employees (id, name, email, salary, active) 
            VALUES (1, 'John Doe', 'john@example.com', 75000.00, true);
            
            -- Select all data
            SELECT * FROM employees;
            
            -- Select with WHERE clause
            SELECT name, salary FROM employees WHERE active = true;
            
            -- Select with LIMIT
            SELECT * FROM products LIMIT 5;
            
            -- Update data
            UPDATE employees SET salary = 80000.00 WHERE id = 1;
            
            -- Delete data
            DELETE FROM employees WHERE id = 1;
            
            -- Join tables
            SELECT * FROM products INNER JOIN categories 
            ON products.category_id = categories.id;
            
            ═══════════════════════════════════════════════════════════════════
            
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
                    // Clear screen (ANSI escape code)
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                    continue;
                }
            }
            
            // Build query (support multi-line with semicolon termination)
            multiLineQuery.append(line).append(" ");
            
            // Check if query is complete (ends with semicolon)
            String query = multiLineQuery.toString().trim();
            if (query.endsWith(";")) {
                inMultiLine = false;
                multiLineQuery = new StringBuilder();
                
                // Remove trailing semicolon for parsing
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
        } else if (result instanceof DatabaseTable table) {
            // DESCRIBE result
            printTableDescription(table);
        } else if (result instanceof List<?> list) {
            if (list.isEmpty()) {
                System.out.println("Empty set (0 rows)");
            } else {
                Object first = list.get(0);
                if (first instanceof DatabaseTable) {
                    // SHOW TABLES result
                    printTablesList((List<DatabaseTable>) list);
                } else if (first instanceof Map) {
                    // SELECT result
                    printResultSet((List<Map<String, Object>>) list);
                } else {
                    // Unknown list type - print as JSON
                    printAsJson(result);
                }
            }
        } else if (result instanceof com.pesapal.rdbms.entity.TableRow) {
            // INSERT result
            System.out.println("1 row inserted.");
        } else if (result instanceof Integer count) {
            // UPDATE/DELETE result
            System.out.println(count + " row(s) affected.");
        } else {
            // Unknown type - print as JSON
            printAsJson(result);
        }
        
        System.out.printf("Time: %d ms%n%n", duration);
    }

    private void printTablesList(List<DatabaseTable> tables) {
        if (tables.isEmpty()) {
            System.out.println("No tables found.");
            return;
        }

        // Calculate column widths
        int maxNameLen = "Table Name".length();
        int maxColsLen = "Columns".length();
        int maxRowsLen = "Rows".length();
        
        for (DatabaseTable t : tables) {
            maxNameLen = Math.max(maxNameLen, t.getTableName().length());
            maxColsLen = Math.max(maxColsLen, String.valueOf(t.getColumns().size()).length());
            maxRowsLen = Math.max(maxRowsLen, String.valueOf(t.getRows().size()).length());
        }
        
        String format = "| %-" + maxNameLen + "s | %" + maxColsLen + "s | %" + maxRowsLen + "s |%n";
        String separator = "+" + "-".repeat(maxNameLen + 2) + "+" + "-".repeat(maxColsLen + 2) + "+" + "-".repeat(maxRowsLen + 2) + "+";
        
        System.out.println(separator);
        System.out.printf(format, "Table Name", "Columns", "Rows");
        System.out.println(separator);
        
        for (DatabaseTable t : tables) {
            System.out.printf(format, t.getTableName(), t.getColumns().size(), t.getRows().size());
        }
        
        System.out.println(separator);
        System.out.println(tables.size() + " table(s) in database");
    }

    private void printTableDescription(DatabaseTable table) {
        System.out.println("Table: " + table.getTableName());
        System.out.println();
        
        // Calculate column widths for columns info
        int maxNameLen = "Column".length();
        int maxTypeLen = "Type".length();
        int maxNullLen = "Nullable".length();
        int maxKeyLen = "Key".length();
        
        for (TableColumn col : table.getColumns()) {
            maxNameLen = Math.max(maxNameLen, col.getColumnName().length());
            String typeStr = formatDataType(col);
            maxTypeLen = Math.max(maxTypeLen, typeStr.length());
        }
        
        String format = "| %-" + maxNameLen + "s | %-" + maxTypeLen + "s | %-" + maxNullLen + "s | %-" + maxKeyLen + "s |%n";
        String separator = "+" + "-".repeat(maxNameLen + 2) + "+" + "-".repeat(maxTypeLen + 2) + "+" + "-".repeat(maxNullLen + 2) + "+" + "-".repeat(maxKeyLen + 2) + "+";
        
        System.out.println(separator);
        System.out.printf(format, "Column", "Type", "Nullable", "Key");
        System.out.println(separator);
        
        for (TableColumn col : table.getColumns()) {
            String keyInfo = getKeyInfo(table, col.getColumnName());
            System.out.printf(format, 
                col.getColumnName(), 
                formatDataType(col),
                col.getNullable() ? "YES" : "NO",
                keyInfo
            );
        }
        
        System.out.println(separator);
        System.out.println(table.getColumns().size() + " column(s)");
        
        // Print indexes if any
        if (!table.getIndexes().isEmpty()) {
            System.out.println();
            System.out.println("Indexes:");
            for (var idx : table.getIndexes()) {
                System.out.printf("  - %s on %s%s%n", 
                    idx.getIndexName(), 
                    idx.getColumnName(),
                    idx.getUnique() ? " (UNIQUE)" : ""
                );
            }
        }
    }

    private String formatDataType(TableColumn col) {
        String type = col.getDataType().toString();
        if (col.getMaxLength() != null && col.getMaxLength() > 0) {
            type += "(" + col.getMaxLength() + ")";
        }
        return type;
    }

    private String getKeyInfo(DatabaseTable table, String columnName) {
        for (TableKey key : table.getKeys()) {
            if (key.getColumnName().equals(columnName)) {
                return key.getKeyType() == TableKey.KeyType.PRIMARY ? "PRI" : "UNI";
            }
        }
        return "";
    }

    private void printResultSet(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            System.out.println("Empty set (0 rows)");
            return;
        }
        
        // Get all column names
        List<String> columns = rows.get(0).keySet().stream().sorted().collect(Collectors.toList());
        
        // Calculate column widths
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
        
        // Build format string
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
        
        // Print header
        System.out.println(separator);
        System.out.printf(format, columns.toArray());
        System.out.println(separator);
        
        // Print rows
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
