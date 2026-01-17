package com.pesapal.rdbms.service;

import com.pesapal.rdbms.dto.*;
import com.pesapal.rdbms.entity.TableColumn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqlParserService {

    private final RdbmsService rdbmsService;

    public Object executeSql(String sql) {
        sql = sql.trim().replaceAll("\\s+", " ");
        
        if (sql.toUpperCase().startsWith("CREATE TABLE")) {
            return parseCreateTable(sql);
        } else if (sql.toUpperCase().startsWith("INSERT INTO")) {
            return parseInsert(sql);
        } else if (sql.toUpperCase().startsWith("SELECT")) {
            return parseSelect(sql);
        } else if (sql.toUpperCase().startsWith("UPDATE")) {
            return parseUpdate(sql);
        } else if (sql.toUpperCase().startsWith("DELETE FROM")) {
            return parseDelete(sql);
        } else if (sql.toUpperCase().contains("JOIN")) {
            return parseJoin(sql);
        } else if (sql.toUpperCase().startsWith("SHOW TABLES")) {
            return rdbmsService.listTables();
        } else if (sql.toUpperCase().startsWith("DESCRIBE") || sql.toUpperCase().startsWith("DESC")) {
            return parseDescribe(sql);
        } else {
            throw new IllegalArgumentException("Unsupported SQL statement: " + sql);
        }
    }

    private Object parseCreateTable(String sql) {
        Pattern pattern = Pattern.compile(
                "CREATE TABLE\\s+(\\w+)\\s*\\(([^)]+)\\)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(sql);
        
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid CREATE TABLE syntax");
        }

        String tableName = matcher.group(1);
        String columnsDef = matcher.group(2);

        CreateTableRequest request = new CreateTableRequest();
        request.setTableName(tableName);
        request.setColumns(new ArrayList<>());
        request.setPrimaryKeys(new ArrayList<>());
        request.setUniqueKeys(new ArrayList<>());
        request.setIndexes(new ArrayList<>());

        String[] columnDefs = columnsDef.split(",");
        for (String colDef : columnDefs) {
            colDef = colDef.trim();
            
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
            } else {
                CreateTableRequest.ColumnDefinition column = parseColumnDefinition(colDef);
                request.getColumns().add(column);
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
            column.setDataType(TableColumn.DataType.VARCHAR);
            Pattern lengthPattern = Pattern.compile("\\((\\d+)\\)");
            Matcher lengthMatcher = lengthPattern.matcher(dataTypeStr);
            if (lengthMatcher.find()) {
                column.setMaxLength(Integer.parseInt(lengthMatcher.group(1)));
            }
        } else if (dataTypeStr.contains("INT") || dataTypeStr.equals("INTEGER")) {
            column.setDataType(TableColumn.DataType.INTEGER);
        } else if (dataTypeStr.contains("BIGINT")) {
            column.setDataType(TableColumn.DataType.BIGINT);
        } else if (dataTypeStr.contains("DECIMAL") || dataTypeStr.contains("NUMERIC")) {
            column.setDataType(TableColumn.DataType.DECIMAL);
        } else if (dataTypeStr.contains("BOOLEAN") || dataTypeStr.contains("BOOL")) {
            column.setDataType(TableColumn.DataType.BOOLEAN);
        } else if (dataTypeStr.contains("DATE")) {
            column.setDataType(TableColumn.DataType.DATE);
        } else if (dataTypeStr.contains("TIMESTAMP")) {
            column.setDataType(TableColumn.DataType.TIMESTAMP);
        } else if (dataTypeStr.contains("TEXT")) {
            column.setDataType(TableColumn.DataType.TEXT);
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + dataTypeStr);
        }

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

        // Parse OFFSET
        Pattern offsetPattern = Pattern.compile("OFFSET\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher offsetMatcher = offsetPattern.matcher(sql);
        if (offsetMatcher.find()) {
            request.setOffset(Integer.parseInt(offsetMatcher.group(1)));
        }

        return rdbmsService.select(request);
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
                "FROM\\s+(\\w+)\\s+(INNER|LEFT|RIGHT)?\\s*JOIN\\s+(\\w+)\\s+ON\\s+(\\w+)\\.(\\w+)\\s*=\\s*(\\w+)\\.(\\w+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher joinMatcher = joinPattern.matcher(sql);
        if (joinMatcher.find()) {
            request.setLeftTable(joinMatcher.group(1));
            String joinType = joinMatcher.group(2);
            if (joinType != null) {
                request.setJoinType(JoinRequest.JoinType.valueOf(joinType.toUpperCase()));
            }
            request.setRightTable(joinMatcher.group(3));
            request.setLeftColumn(joinMatcher.group(5));
            request.setRightColumn(joinMatcher.group(7));
        }

        // Parse WHERE clause
        Pattern wherePattern = Pattern.compile("WHERE\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher whereMatcher = wherePattern.matcher(sql);
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1).trim();
            request.setWhere(parseWhereClause(whereClause));
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
        // Simple equality conditions only
        String[] conditions = whereClause.split("AND");
        for (String condition : conditions) {
            condition = condition.trim();
            if (condition.contains("=")) {
                String[] parts = condition.split("=", 2);
                String key = parts[0].trim();
                String valueStr = parts[1].trim();
                where.put(key, parseValue(valueStr));
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
}
