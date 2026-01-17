package com.pesapal.rdbms.service;

import com.pesapal.rdbms.dto.*;
import com.pesapal.rdbms.exception.InvalidSqlException;
import com.pesapal.rdbms.storage.DataType;
import com.pesapal.rdbms.storage.TableSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL Parser for the file-based RDBMS.
 * 
 * <p>This service parses SQL statements and translates them into method calls
 * on the {@link FileBasedRdbmsService}. It supports a subset of standard SQL
 * including DDL and DML operations.</p>
 * 
 * <h2>Supported SQL Statements</h2>
 * <ul>
 *   <li><b>DDL:</b> CREATE TABLE, DROP TABLE</li>
 *   <li><b>DML:</b> INSERT, SELECT, UPDATE, DELETE</li>
 *   <li><b>Queries:</b> WHERE, ORDER BY, LIMIT, OFFSET, JOIN</li>
 *   <li><b>Utility:</b> SHOW TABLES, SHOW INDEXES, DESCRIBE, EXPLAIN</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Via the service
 * Object result = sqlParserService.executeSql("SELECT * FROM users WHERE id = 1");
 * }</pre>
 * 
 * @author Pesapal RDBMS Team
 * @version 2.1
 * @see FileBasedRdbmsService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileBasedSqlParserService {
    
    private final FileBasedRdbmsService rdbmsService;
    
    /**
     * Executes a SQL statement and returns the result.
     *
     * @param sql the SQL statement to execute
     * @return the result of the SQL operation (varies by statement type)
     * @throws InvalidSqlException if the SQL syntax is invalid
     * @throws com.pesapal.rdbms.exception.TableNotFoundException if a referenced table doesn't exist
     * @throws com.pesapal.rdbms.exception.ConstraintViolationException if a constraint is violated
     */
    public Object executeSql(String sql) {
        // Validate input
        if (sql == null || sql.isBlank()) {
            throw new InvalidSqlException("SQL statement cannot be empty");
        }
        
        // Normalize whitespace
        sql = sql.trim().replaceAll("\\s+", " ");
        
        // Remove trailing semicolon if present
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        
        log.debug("Executing SQL: {}", sql);
        
        // Route to appropriate parser based on statement type
        String upperSql = sql.toUpperCase();
        
        if (upperSql.startsWith("EXPLAIN ")) {
            return parseExplain(sql);
        } else if (upperSql.startsWith("CREATE TABLE")) {
            return parseCreateTable(sql);
        } else if (upperSql.startsWith("DROP TABLE")) {
            return parseDropTable(sql);
        } else if (upperSql.startsWith("INSERT INTO")) {
            return parseInsert(sql);
        } else if (upperSql.startsWith("SELECT")) {
            // Check if it's a JOIN
            if (upperSql.contains(" JOIN ")) {
                return parseJoin(sql);
            }
            return parseSelect(sql);
        } else if (upperSql.startsWith("UPDATE")) {
            return parseUpdate(sql);
        } else if (upperSql.startsWith("DELETE FROM")) {
            return parseDelete(sql);
        } else if (upperSql.startsWith("SHOW TABLES")) {
            return rdbmsService.listTables();
        } else if (upperSql.startsWith("SHOW INDEXES")) {
            return rdbmsService.getIndexStats();
        } else if (upperSql.startsWith("DESCRIBE") || upperSql.startsWith("DESC")) {
            return parseDescribe(sql);
        } else {
            throw new InvalidSqlException(
                    "Unsupported SQL statement. Supported: SELECT, INSERT, UPDATE, DELETE, " +
                    "CREATE TABLE, DROP TABLE, SHOW TABLES, SHOW INDEXES, DESCRIBE, EXPLAIN", sql);
        }
    }
    
    /**
     * Parses EXPLAIN command.
     * Executes the query and returns the execution plan.
     */
    private Object parseExplain(String sql) {
        // Remove EXPLAIN prefix and execute the query
        String innerSql = sql.substring("EXPLAIN ".length()).trim();
        
        // Execute the inner query
        executeSql(innerSql);
        
        // Get the execution plan from the last query
        var execution = rdbmsService.getLastQueryExecution();
        if (execution == null) {
            return Map.of("message", "No execution plan available");
        }
        
        // Return formatted execution plan
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("query", innerSql);
        plan.put("table", execution.getTableName());
        plan.put("queryType", execution.getQueryType());
        plan.put("accessMethod", execution.isIndexUsed() ? "INDEX LOOKUP" : "FULL TABLE SCAN");
        
        if (execution.isIndexUsed()) {
            plan.put("indexName", execution.getIndexName());
            plan.put("indexColumn", execution.getIndexColumn());
            plan.put("indexOperation", execution.getIndexOperation());
        }
        
        plan.put("rowsScanned", execution.getRowsScanned());
        plan.put("rowsReturned", execution.getRowsReturned());
        plan.put("executionTimeMs", execution.getExecutionTimeMs());
        plan.put("executionPlan", execution.getExecutionPlan());
        
        return plan;
    }
    
    private Object parseDropTable(String sql) {
        Pattern pattern = Pattern.compile(
                "DROP TABLE(?:\\s+IF\\s+EXISTS)?\\s+(\\w+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(sql);
        
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid DROP TABLE syntax");
        }
        
        String tableName = matcher.group(1);
        rdbmsService.dropTable(tableName);
        return "Table '" + tableName + "' dropped successfully";
    }
    
    private Object parseCreateTable(String sql) {
        // Extract table name
        Pattern tableNamePattern = Pattern.compile(
                "CREATE TABLE\\s+(\\w+)\\s*\\(",
                Pattern.CASE_INSENSITIVE
        );
        Matcher tableNameMatcher = tableNamePattern.matcher(sql);
        
        if (!tableNameMatcher.find()) {
            throw new IllegalArgumentException("Invalid CREATE TABLE syntax");
        }
        
        String tableName = tableNameMatcher.group(1);
        
        // Extract everything between the outer parentheses (handle nested parens)
        int startParen = sql.indexOf('(');
        int endParen = sql.lastIndexOf(')');
        
        if (startParen == -1 || endParen == -1 || endParen <= startParen) {
            throw new IllegalArgumentException("Invalid CREATE TABLE syntax: missing parentheses");
        }
        
        String columnsDef = sql.substring(startParen + 1, endParen);
        
        CreateTableRequest request = new CreateTableRequest();
        request.setTableName(tableName);
        request.setColumns(new ArrayList<>());
        request.setPrimaryKeys(new ArrayList<>());
        request.setUniqueKeys(new ArrayList<>());
        request.setIndexes(new ArrayList<>());
        
        // Split by comma, but respect parentheses
        List<String> columnDefsList = splitByCommaRespectingParens(columnsDef);
        
        for (String colDef : columnDefsList) {
            colDef = colDef.trim();
            
            if (colDef.isEmpty()) continue;
            
            if (colDef.toUpperCase().startsWith("PRIMARY KEY")) {
                Pattern pkPattern = Pattern.compile("PRIMARY KEY\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
                Matcher pkMatcher = pkPattern.matcher(colDef);
                if (pkMatcher.find()) {
                    String[] pkCols = pkMatcher.group(1).split(",");
                    for (String pkCol : pkCols) {
                        request.getPrimaryKeys().add(pkCol.trim());
                    }
                }
            } else if (colDef.toUpperCase().startsWith("UNIQUE")) {
                Pattern ukPattern = Pattern.compile("UNIQUE\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
                Matcher ukMatcher = ukPattern.matcher(colDef);
                if (ukMatcher.find()) {
                    String[] ukCols = ukMatcher.group(1).split(",");
                    for (String ukCol : ukCols) {
                        request.getUniqueKeys().add(ukCol.trim());
                    }
                }
            } else if (colDef.toUpperCase().startsWith("INDEX") || colDef.toUpperCase().startsWith("KEY")) {
                Pattern idxPattern = Pattern.compile("(?:INDEX|KEY)\\s+(\\w+)\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
                Matcher idxMatcher = idxPattern.matcher(colDef);
                if (idxMatcher.find()) {
                    CreateTableRequest.IndexDefinition idx = new CreateTableRequest.IndexDefinition();
                    idx.setIndexName(idxMatcher.group(1));
                    idx.setColumnName(idxMatcher.group(2).trim());
                    idx.setUnique(false);
                    request.getIndexes().add(idx);
                }
            } else {
                CreateTableRequest.ColumnDefinition column = parseColumnDefinition(colDef);
                if (column != null) {
                    request.getColumns().add(column);
                }
            }
        }
        
        return rdbmsService.createTable(request);
    }
    
    private CreateTableRequest.ColumnDefinition parseColumnDefinition(String colDef) {
        String[] parts = colDef.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid column definition: " + colDef);
        }
        
        CreateTableRequest.ColumnDefinition column = new CreateTableRequest.ColumnDefinition();
        column.setName(parts[0].trim());
        
        String dataTypeStr = parts[1].toUpperCase();
        if (dataTypeStr.contains("VARCHAR") || dataTypeStr.contains("CHAR")) {
            column.setDataType(DataType.VARCHAR);
            Pattern lengthPattern = Pattern.compile("\\((\\d+)\\)");
            Matcher lengthMatcher = lengthPattern.matcher(dataTypeStr);
            if (lengthMatcher.find()) {
                column.setMaxLength(Integer.parseInt(lengthMatcher.group(1)));
            }
        } else if (dataTypeStr.equals("INT") || dataTypeStr.equals("INTEGER")) {
            column.setDataType(DataType.INTEGER);
        } else if (dataTypeStr.contains("BIGINT")) {
            column.setDataType(DataType.BIGINT);
        } else if (dataTypeStr.contains("DECIMAL") || dataTypeStr.contains("NUMERIC") || dataTypeStr.contains("DOUBLE") || dataTypeStr.contains("FLOAT")) {
            column.setDataType(DataType.DECIMAL);
        } else if (dataTypeStr.contains("BOOLEAN") || dataTypeStr.contains("BOOL")) {
            column.setDataType(DataType.BOOLEAN);
        } else if (dataTypeStr.contains("DATE") && !dataTypeStr.contains("TIME")) {
            column.setDataType(DataType.DATE);
        } else if (dataTypeStr.contains("TIMESTAMP") || dataTypeStr.contains("DATETIME")) {
            column.setDataType(DataType.TIMESTAMP);
        } else if (dataTypeStr.contains("TEXT") || dataTypeStr.contains("CLOB")) {
            column.setDataType(DataType.TEXT);
        } else {
            // Default to VARCHAR for unknown types
            column.setDataType(DataType.VARCHAR);
        }
        
        // Parse constraints
        for (int i = 2; i < parts.length; i++) {
            String part = parts[i].toUpperCase();
            if (part.equals("NOT") && i + 1 < parts.length && parts[i + 1].toUpperCase().equals("NULL")) {
                column.setNullable(false);
                i++;
            } else if (part.equals("NULL")) {
                column.setNullable(true);
            } else if (part.equals("DEFAULT") && i + 1 < parts.length) {
                String defaultValue = parts[i + 1];
                if (defaultValue.startsWith("'") && defaultValue.endsWith("'")) {
                    column.setDefaultValue(defaultValue.substring(1, defaultValue.length() - 1));
                } else {
                    column.setDefaultValue(defaultValue);
                }
                i++;
            }
        }
        
        return column;
    }
    
    private Object parseInsert(String sql) {
        Pattern pattern = Pattern.compile(
                "INSERT INTO\\s+(\\w+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(sql);
        
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid INSERT syntax");
        }
        
        String tableName = matcher.group(1);
        String[] columns = matcher.group(2).split(",");
        String[] values = matcher.group(3).split(",");
        
        if (columns.length != values.length) {
            throw new IllegalArgumentException("Column count doesn't match value count");
        }
        
        InsertRequest request = new InsertRequest();
        request.setTableName(tableName);
        request.setValues(new HashMap<>());
        
        for (int i = 0; i < columns.length; i++) {
            String colName = columns[i].trim();
            String valueStr = values[i].trim();
            Object value = parseValue(valueStr);
            request.getValues().put(colName, value);
        }
        
        return rdbmsService.insert(request);
    }
    
    private Object parseSelect(String sql) {
        SelectRequest request = new SelectRequest();
        
        // Parse SELECT columns
        Pattern selectPattern = Pattern.compile("SELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE);
        Matcher selectMatcher = selectPattern.matcher(sql);
        if (selectMatcher.find()) {
            String columnsStr = selectMatcher.group(1).trim();
            if (columnsStr.equals("*")) {
                request.setColumns(null);
            } else {
                request.setColumns(Arrays.stream(columnsStr.split(","))
                        .map(String::trim)
                        .collect(Collectors.toList()));
            }
        }
        
        // Parse FROM table
        Pattern fromPattern = Pattern.compile("FROM\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher fromMatcher = fromPattern.matcher(sql);
        if (fromMatcher.find()) {
            request.setTableName(fromMatcher.group(1));
        }
        
        // Parse WHERE clause
        Pattern wherePattern = Pattern.compile("WHERE\\s+(.+?)(?:\\s+ORDER|\\s+LIMIT|\\s+OFFSET|$)", Pattern.CASE_INSENSITIVE);
        Matcher whereMatcher = wherePattern.matcher(sql);
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1).trim();
            request.setWhere(parseWhereClause(whereClause));
        }
        
        // Parse ORDER BY clause
        Pattern orderByPattern = Pattern.compile("ORDER\\s+BY\\s+(.+?)(?:\\s+LIMIT|\\s+OFFSET|$)", Pattern.CASE_INSENSITIVE);
        Matcher orderByMatcher = orderByPattern.matcher(sql);
        if (orderByMatcher.find()) {
            String orderByClause = orderByMatcher.group(1).trim();
            request.setOrderBy(parseOrderByClause(orderByClause));
        }
        
        // Parse LIMIT
        Pattern limitPattern = Pattern.compile("LIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher limitMatcher = limitPattern.matcher(sql);
        if (limitMatcher.find()) {
            request.setLimit(Integer.parseInt(limitMatcher.group(1)));
        }
        
        // Parse OFFSET
        Pattern offsetPattern = Pattern.compile("OFFSET\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher offsetMatcher = offsetPattern.matcher(sql);
        if (offsetMatcher.find()) {
            request.setOffset(Integer.parseInt(offsetMatcher.group(1)));
        }
        
        return rdbmsService.select(request);
    }
    
    /**
     * Parses ORDER BY clause into list of OrderBy objects.
     * Examples: "name ASC", "price DESC, name ASC"
     */
    private List<SelectRequest.OrderBy> parseOrderByClause(String orderByClause) {
        List<SelectRequest.OrderBy> orderByList = new ArrayList<>();
        
        String[] parts = orderByClause.split(",");
        for (String part : parts) {
            part = part.trim();
            String[] tokens = part.split("\\s+");
            
            String column = tokens[0].trim();
            boolean descending = false;
            
            if (tokens.length > 1) {
                String direction = tokens[1].trim().toUpperCase();
                descending = direction.equals("DESC");
            }
            
            orderByList.add(new SelectRequest.OrderBy(column, descending));
        }
        
        return orderByList;
    }
    
    private Object parseUpdate(String sql) {
        UpdateRequest request = new UpdateRequest();
        
        // Parse UPDATE table
        Pattern updatePattern = Pattern.compile("UPDATE\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher updateMatcher = updatePattern.matcher(sql);
        if (updateMatcher.find()) {
            request.setTableName(updateMatcher.group(1));
        }
        
        // Parse SET clause
        Pattern setPattern = Pattern.compile("SET\\s+(.+?)(?:\\s+WHERE|$)", Pattern.CASE_INSENSITIVE);
        Matcher setMatcher = setPattern.matcher(sql);
        if (setMatcher.find()) {
            String setClause = setMatcher.group(1).trim();
            request.setSet(parseSetClause(setClause));
        }
        
        // Parse WHERE clause
        Pattern wherePattern = Pattern.compile("WHERE\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher whereMatcher = wherePattern.matcher(sql);
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1).trim();
            request.setWhere(parseWhereClause(whereClause));
        }
        
        return rdbmsService.update(request);
    }
    
    private Object parseDelete(String sql) {
        DeleteRequest request = new DeleteRequest();
        
        // Parse DELETE FROM table
        Pattern deletePattern = Pattern.compile("DELETE FROM\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher deleteMatcher = deletePattern.matcher(sql);
        if (deleteMatcher.find()) {
            request.setTableName(deleteMatcher.group(1));
        }
        
        // Parse WHERE clause
        Pattern wherePattern = Pattern.compile("WHERE\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher whereMatcher = wherePattern.matcher(sql);
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1).trim();
            request.setWhere(parseWhereClause(whereClause));
        }
        
        return rdbmsService.delete(request);
    }
    
    private Object parseJoin(String sql) {
        JoinRequest request = new JoinRequest();
        
        // Parse SELECT columns
        Pattern selectPattern = Pattern.compile("SELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE);
        Matcher selectMatcher = selectPattern.matcher(sql);
        if (selectMatcher.find()) {
            String columnsStr = selectMatcher.group(1).trim();
            if (!columnsStr.equals("*")) {
                request.setColumns(Arrays.stream(columnsStr.split(","))
                        .map(String::trim)
                        .collect(Collectors.toList()));
            }
        }
        
        // Parse FROM and JOIN
        Pattern joinPattern = Pattern.compile(
                "FROM\\s+(\\w+)(?:\\s+\\w+)?\\s+(INNER|LEFT|RIGHT)?\\s*JOIN\\s+(\\w+)(?:\\s+\\w+)?\\s+ON\\s+(\\w+)\\.(\\w+)\\s*=\\s*(\\w+)\\.(\\w+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher joinMatcher = joinPattern.matcher(sql);
        if (joinMatcher.find()) {
            request.setLeftTable(joinMatcher.group(1));
            String joinType = joinMatcher.group(2);
            if (joinType != null) {
                request.setJoinType(JoinRequest.JoinType.valueOf(joinType.toUpperCase()));
            } else {
                request.setJoinType(JoinRequest.JoinType.INNER);
            }
            request.setRightTable(joinMatcher.group(3));
            request.setLeftColumn(joinMatcher.group(5));
            request.setRightColumn(joinMatcher.group(7));
        }
        
        // Parse WHERE clause
        Pattern wherePattern = Pattern.compile("WHERE\\s+(.+?)(?:\\s+LIMIT|\\s+OFFSET|$)", Pattern.CASE_INSENSITIVE);
        Matcher whereMatcher = wherePattern.matcher(sql);
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1).trim();
            request.setWhere(parseWhereClause(whereClause));
        }
        
        // Parse LIMIT
        Pattern limitPattern = Pattern.compile("LIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher limitMatcher = limitPattern.matcher(sql);
        if (limitMatcher.find()) {
            request.setLimit(Integer.parseInt(limitMatcher.group(1)));
        }
        
        return rdbmsService.join(request);
    }
    
    private Object parseDescribe(String sql) {
        Pattern pattern = Pattern.compile("(?:DESCRIBE|DESC)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return rdbmsService.getTable(matcher.group(1));
        }
        throw new IllegalArgumentException("Invalid DESCRIBE syntax");
    }
    
    private Map<String, Object> parseWhereClause(String whereClause) {
        Map<String, Object> where = new HashMap<>();
        
        // Split by AND (case insensitive)
        String[] conditions = whereClause.split("(?i)\\s+AND\\s+");
        
        for (String condition : conditions) {
            condition = condition.trim();
            
            // Handle IS NULL / IS NOT NULL
            Pattern isNullPattern = Pattern.compile("(\\w+(?:\\.\\w+)?)\\s+IS\\s+(NOT\\s+)?NULL", Pattern.CASE_INSENSITIVE);
            Matcher isNullMatcher = isNullPattern.matcher(condition);
            if (isNullMatcher.find()) {
                String key = isNullMatcher.group(1).trim();
                boolean isNotNull = isNullMatcher.group(2) != null;
                Map<String, Object> opCondition = new HashMap<>();
                opCondition.put("op", isNotNull ? "IS NOT NULL" : "IS NULL");
                opCondition.put("value", null);
                where.put(key, opCondition);
                continue;
            }
            
            // Handle LIKE
            Pattern likePattern = Pattern.compile("(\\w+(?:\\.\\w+)?)\\s+LIKE\\s+(.+)", Pattern.CASE_INSENSITIVE);
            Matcher likeMatcher = likePattern.matcher(condition);
            if (likeMatcher.find()) {
                String key = likeMatcher.group(1).trim();
                String valueStr = likeMatcher.group(2).trim();
                Map<String, Object> opCondition = new HashMap<>();
                opCondition.put("op", "LIKE");
                opCondition.put("value", parseValue(valueStr));
                where.put(key, opCondition);
                continue;
            }
            
            // Handle comparison operators: >=, <=, <>, !=, >, <, =
            Pattern compPattern = Pattern.compile("(\\w+(?:\\.\\w+)?)\\s*(>=|<=|<>|!=|>|<|=)\\s*(.+)");
            Matcher compMatcher = compPattern.matcher(condition);
            if (compMatcher.find()) {
                String key = compMatcher.group(1).trim();
                String operator = compMatcher.group(2).trim();
                String valueStr = compMatcher.group(3).trim();
                
                if (operator.equals("=")) {
                    where.put(key, parseValue(valueStr));
                } else {
                    Map<String, Object> opCondition = new HashMap<>();
                    opCondition.put("op", operator);
                    opCondition.put("value", parseValue(valueStr));
                    where.put(key, opCondition);
                }
            }
        }
        return where;
    }
    
    private Map<String, Object> parseSetClause(String setClause) {
        Map<String, Object> set = new HashMap<>();
        String[] assignments = setClause.split(",");
        for (String assignment : assignments) {
            assignment = assignment.trim();
            if (assignment.contains("=")) {
                String[] parts = assignment.split("=", 2);
                String key = parts[0].trim();
                String valueStr = parts[1].trim();
                set.put(key, parseValue(valueStr));
            }
        }
        return set;
    }
    
    private Object parseValue(String valueStr) {
        valueStr = valueStr.trim();
        if (valueStr.startsWith("'") && valueStr.endsWith("'")) {
            return valueStr.substring(1, valueStr.length() - 1);
        } else if (valueStr.equalsIgnoreCase("true") || valueStr.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(valueStr);
        } else if (valueStr.matches("^-?\\d+$")) {
            return Integer.parseInt(valueStr);
        } else if (valueStr.matches("^-?\\d+\\.\\d+$")) {
            return Double.parseDouble(valueStr);
        } else if (valueStr.equalsIgnoreCase("NULL")) {
            return null;
        } else {
            return valueStr;
        }
    }
    
    /**
     * Splits a string by comma, but respects parentheses.
     * For example: "id INTEGER, PRIMARY KEY (id), UNIQUE (email)"
     * becomes: ["id INTEGER", "PRIMARY KEY (id)", "UNIQUE (email)"]
     */
    private List<String> splitByCommaRespectingParens(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;
        
        for (char c : input.toCharArray()) {
            if (c == '(') {
                parenDepth++;
                current.append(c);
            } else if (c == ')') {
                parenDepth--;
                current.append(c);
            } else if (c == ',' && parenDepth == 0) {
                // Split here - we're not inside parentheses
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        // Don't forget the last part
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        
        return result;
    }
}
