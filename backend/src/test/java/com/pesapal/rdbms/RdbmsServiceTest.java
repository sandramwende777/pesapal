package com.pesapal.rdbms;

import com.pesapal.rdbms.dto.*;
import com.pesapal.rdbms.entity.*;
import com.pesapal.rdbms.service.RdbmsService;
import com.pesapal.rdbms.service.SqlParserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the RDBMS Service.
 * 
 * Tests all core functionality: CREATE TABLE, INSERT, SELECT, UPDATE, DELETE, JOIN
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RdbmsServiceTest {

    @Autowired
    private RdbmsService rdbmsService;

    @Autowired
    private SqlParserService sqlParserService;

    // ==================== CREATE TABLE Tests ====================

    @Test
    void testCreateTable() {
        CreateTableRequest request = createTestTableRequest("test_users");
        
        DatabaseTable table = rdbmsService.createTable(request);
        
        assertNotNull(table);
        assertEquals("test_users", table.getTableName());
        assertEquals(3, table.getColumns().size());
    }

    @Test
    void testCreateTableWithPrimaryKey() {
        CreateTableRequest request = createTestTableRequest("pk_test");
        request.setPrimaryKeys(List.of("id"));
        
        DatabaseTable table = rdbmsService.createTable(request);
        
        assertTrue(table.getKeys().stream()
            .anyMatch(k -> k.getKeyType() == TableKey.KeyType.PRIMARY && k.getColumnName().equals("id")));
    }

    @Test
    void testCreateTableWithUniqueKey() {
        CreateTableRequest request = createTestTableRequest("uk_test");
        request.setUniqueKeys(List.of("email"));
        
        DatabaseTable table = rdbmsService.createTable(request);
        
        assertTrue(table.getKeys().stream()
            .anyMatch(k -> k.getKeyType() == TableKey.KeyType.UNIQUE && k.getColumnName().equals("email")));
    }

    @Test
    void testCreateDuplicateTableFails() {
        CreateTableRequest request = createTestTableRequest("duplicate_test");
        rdbmsService.createTable(request);
        
        assertThrows(IllegalArgumentException.class, () -> {
            rdbmsService.createTable(request);
        });
    }

    // ==================== INSERT Tests ====================

    @Test
    void testInsert() {
        createTestTable("insert_test");
        
        InsertRequest insertRequest = new InsertRequest();
        insertRequest.setTableName("insert_test");
        insertRequest.setValues(Map.of(
            "id", 1,
            "name", "John Doe",
            "email", "john@example.com"
        ));
        
        TableRow row = rdbmsService.insert(insertRequest);
        
        assertNotNull(row);
        assertNotNull(row.getId());
    }

    @Test
    void testInsertDuplicatePrimaryKeyFails() {
        CreateTableRequest tableRequest = createTestTableRequest("pk_insert_test");
        tableRequest.setPrimaryKeys(List.of("id"));
        rdbmsService.createTable(tableRequest);
        
        InsertRequest insertRequest = new InsertRequest();
        insertRequest.setTableName("pk_insert_test");
        insertRequest.setValues(Map.of("id", 1, "name", "John", "email", "john@example.com"));
        rdbmsService.insert(insertRequest);
        
        InsertRequest duplicateRequest = new InsertRequest();
        duplicateRequest.setTableName("pk_insert_test");
        duplicateRequest.setValues(Map.of("id", 1, "name", "Jane", "email", "jane@example.com"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            rdbmsService.insert(duplicateRequest);
        });
    }

    @Test
    void testInsertDuplicateUniqueKeyFails() {
        CreateTableRequest tableRequest = createTestTableRequest("uk_insert_test");
        tableRequest.setUniqueKeys(List.of("email"));
        rdbmsService.createTable(tableRequest);
        
        InsertRequest insertRequest = new InsertRequest();
        insertRequest.setTableName("uk_insert_test");
        insertRequest.setValues(Map.of("id", 1, "name", "John", "email", "john@example.com"));
        rdbmsService.insert(insertRequest);
        
        InsertRequest duplicateRequest = new InsertRequest();
        duplicateRequest.setTableName("uk_insert_test");
        duplicateRequest.setValues(Map.of("id", 2, "name", "Jane", "email", "john@example.com")); // Same email
        
        assertThrows(IllegalArgumentException.class, () -> {
            rdbmsService.insert(duplicateRequest);
        });
    }

    // ==================== SELECT Tests ====================

    @Test
    void testSelectAll() {
        createTestTableWithData("select_test");
        
        SelectRequest selectRequest = new SelectRequest();
        selectRequest.setTableName("select_test");
        
        List<Map<String, Object>> results = rdbmsService.select(selectRequest);
        
        assertEquals(2, results.size());
    }

    @Test
    void testSelectWithWhere() {
        createTestTableWithData("select_where_test");
        
        SelectRequest selectRequest = new SelectRequest();
        selectRequest.setTableName("select_where_test");
        selectRequest.setWhere(Map.of("id", 1));
        
        List<Map<String, Object>> results = rdbmsService.select(selectRequest);
        
        assertEquals(1, results.size());
        assertEquals("John", results.get(0).get("name"));
    }

    @Test
    void testSelectWithLimit() {
        createTestTableWithData("select_limit_test");
        
        SelectRequest selectRequest = new SelectRequest();
        selectRequest.setTableName("select_limit_test");
        selectRequest.setLimit(1);
        
        List<Map<String, Object>> results = rdbmsService.select(selectRequest);
        
        assertEquals(1, results.size());
    }

    @Test
    void testSelectSpecificColumns() {
        createTestTableWithData("select_cols_test");
        
        SelectRequest selectRequest = new SelectRequest();
        selectRequest.setTableName("select_cols_test");
        selectRequest.setColumns(List.of("name"));
        
        List<Map<String, Object>> results = rdbmsService.select(selectRequest);
        
        assertEquals(2, results.size());
        assertTrue(results.get(0).containsKey("name"));
        assertEquals(1, results.get(0).size()); // Only name column
    }

    // ==================== UPDATE Tests ====================

    @Test
    void testUpdate() {
        createTestTableWithData("update_test");
        
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.setTableName("update_test");
        updateRequest.setSet(Map.of("name", "Updated Name"));
        updateRequest.setWhere(Map.of("id", 1));
        
        int updated = rdbmsService.update(updateRequest);
        
        assertEquals(1, updated);
        
        // Verify update
        SelectRequest selectRequest = new SelectRequest();
        selectRequest.setTableName("update_test");
        selectRequest.setWhere(Map.of("id", 1));
        List<Map<String, Object>> results = rdbmsService.select(selectRequest);
        
        assertEquals("Updated Name", results.get(0).get("name"));
    }

    // ==================== DELETE Tests ====================

    @Test
    void testDelete() {
        createTestTableWithData("delete_test");
        
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.setTableName("delete_test");
        deleteRequest.setWhere(Map.of("id", 1));
        
        int deleted = rdbmsService.delete(deleteRequest);
        
        assertEquals(1, deleted);
        
        // Verify deletion
        SelectRequest selectRequest = new SelectRequest();
        selectRequest.setTableName("delete_test");
        List<Map<String, Object>> results = rdbmsService.select(selectRequest);
        
        assertEquals(1, results.size()); // Only one row remaining
    }

    // ==================== DROP TABLE Tests ====================

    @Test
    void testDropTable() {
        createTestTable("drop_test");
        
        rdbmsService.dropTable("drop_test");
        
        assertThrows(IllegalArgumentException.class, () -> {
            rdbmsService.getTable("drop_test");
        });
    }

    // ==================== JOIN Tests ====================

    @Test
    void testInnerJoin() {
        createJoinTestTables();
        
        JoinRequest joinRequest = new JoinRequest();
        joinRequest.setLeftTable("join_products");
        joinRequest.setRightTable("join_categories");
        joinRequest.setLeftColumn("category_id");
        joinRequest.setRightColumn("id");
        joinRequest.setJoinType(JoinRequest.JoinType.INNER);
        
        List<Map<String, Object>> results = rdbmsService.join(joinRequest);
        
        assertEquals(2, results.size()); // Two products have matching categories
    }

    @Test
    void testLeftJoin() {
        createJoinTestTables();
        
        JoinRequest joinRequest = new JoinRequest();
        joinRequest.setLeftTable("join_products");
        joinRequest.setRightTable("join_categories");
        joinRequest.setLeftColumn("category_id");
        joinRequest.setRightColumn("id");
        joinRequest.setJoinType(JoinRequest.JoinType.LEFT);
        
        List<Map<String, Object>> results = rdbmsService.join(joinRequest);
        
        // All products should be included, even without matching category
        assertTrue(results.size() >= 2);
    }

    // ==================== SQL Parser Tests ====================

    @Test
    void testSqlCreateTable() {
        Object result = sqlParserService.executeSql(
            "CREATE TABLE sql_test (id INTEGER, name VARCHAR(100), PRIMARY KEY (id))"
        );
        
        assertNotNull(result);
        assertTrue(result instanceof DatabaseTable);
        assertEquals("sql_test", ((DatabaseTable) result).getTableName());
    }

    @Test
    void testSqlInsert() {
        sqlParserService.executeSql("CREATE TABLE sql_insert_test (id INTEGER, name VARCHAR(100))");
        
        Object result = sqlParserService.executeSql(
            "INSERT INTO sql_insert_test (id, name) VALUES (1, 'Test User')"
        );
        
        assertNotNull(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSqlSelect() {
        sqlParserService.executeSql("CREATE TABLE sql_select_test (id INTEGER, name VARCHAR(100))");
        sqlParserService.executeSql("INSERT INTO sql_select_test (id, name) VALUES (1, 'Test User')");
        
        Object result = sqlParserService.executeSql("SELECT * FROM sql_select_test");
        
        assertTrue(result instanceof List);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result;
        assertEquals(1, rows.size());
    }

    @Test
    void testSqlDropTable() {
        sqlParserService.executeSql("CREATE TABLE sql_drop_test (id INTEGER)");
        
        Object result = sqlParserService.executeSql("DROP TABLE sql_drop_test");
        
        assertNotNull(result);
        assertThrows(IllegalArgumentException.class, () -> {
            rdbmsService.getTable("sql_drop_test");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSqlShowTables() {
        sqlParserService.executeSql("CREATE TABLE show_test (id INTEGER)");
        
        Object result = sqlParserService.executeSql("SHOW TABLES");
        
        assertTrue(result instanceof List);
        List<DatabaseTable> tables = (List<DatabaseTable>) result;
        assertTrue(tables.stream().anyMatch(t -> t.getTableName().equals("show_test")));
    }

    @Test
    void testSqlDescribe() {
        sqlParserService.executeSql("CREATE TABLE describe_test (id INTEGER, name VARCHAR(100))");
        
        Object result = sqlParserService.executeSql("DESCRIBE describe_test");
        
        assertTrue(result instanceof DatabaseTable);
        DatabaseTable table = (DatabaseTable) result;
        assertEquals(2, table.getColumns().size());
    }

    // ==================== Helper Methods ====================

    private CreateTableRequest createTestTableRequest(String tableName) {
        CreateTableRequest request = new CreateTableRequest();
        request.setTableName(tableName);
        request.setColumns(new ArrayList<>());
        request.setPrimaryKeys(new ArrayList<>());
        request.setUniqueKeys(new ArrayList<>());
        request.setIndexes(new ArrayList<>());

        CreateTableRequest.ColumnDefinition idCol = new CreateTableRequest.ColumnDefinition();
        idCol.setName("id");
        idCol.setDataType(TableColumn.DataType.INTEGER);
        idCol.setNullable(false);
        request.getColumns().add(idCol);

        CreateTableRequest.ColumnDefinition nameCol = new CreateTableRequest.ColumnDefinition();
        nameCol.setName("name");
        nameCol.setDataType(TableColumn.DataType.VARCHAR);
        nameCol.setMaxLength(100);
        request.getColumns().add(nameCol);

        CreateTableRequest.ColumnDefinition emailCol = new CreateTableRequest.ColumnDefinition();
        emailCol.setName("email");
        emailCol.setDataType(TableColumn.DataType.VARCHAR);
        emailCol.setMaxLength(200);
        request.getColumns().add(emailCol);

        return request;
    }

    private void createTestTable(String tableName) {
        rdbmsService.createTable(createTestTableRequest(tableName));
    }

    private void createTestTableWithData(String tableName) {
        createTestTable(tableName);
        
        InsertRequest insert1 = new InsertRequest();
        insert1.setTableName(tableName);
        insert1.setValues(Map.of("id", 1, "name", "John", "email", "john@example.com"));
        rdbmsService.insert(insert1);
        
        InsertRequest insert2 = new InsertRequest();
        insert2.setTableName(tableName);
        insert2.setValues(Map.of("id", 2, "name", "Jane", "email", "jane@example.com"));
        rdbmsService.insert(insert2);
    }

    private void createJoinTestTables() {
        // Create categories table
        CreateTableRequest catRequest = new CreateTableRequest();
        catRequest.setTableName("join_categories");
        catRequest.setColumns(new ArrayList<>());
        catRequest.setPrimaryKeys(List.of("id"));
        catRequest.setUniqueKeys(new ArrayList<>());
        catRequest.setIndexes(new ArrayList<>());

        CreateTableRequest.ColumnDefinition catIdCol = new CreateTableRequest.ColumnDefinition();
        catIdCol.setName("id");
        catIdCol.setDataType(TableColumn.DataType.INTEGER);
        catRequest.getColumns().add(catIdCol);

        CreateTableRequest.ColumnDefinition catNameCol = new CreateTableRequest.ColumnDefinition();
        catNameCol.setName("name");
        catNameCol.setDataType(TableColumn.DataType.VARCHAR);
        catNameCol.setMaxLength(100);
        catRequest.getColumns().add(catNameCol);

        rdbmsService.createTable(catRequest);

        // Create products table
        CreateTableRequest prodRequest = new CreateTableRequest();
        prodRequest.setTableName("join_products");
        prodRequest.setColumns(new ArrayList<>());
        prodRequest.setPrimaryKeys(List.of("id"));
        prodRequest.setUniqueKeys(new ArrayList<>());
        prodRequest.setIndexes(new ArrayList<>());

        CreateTableRequest.ColumnDefinition prodIdCol = new CreateTableRequest.ColumnDefinition();
        prodIdCol.setName("id");
        prodIdCol.setDataType(TableColumn.DataType.INTEGER);
        prodRequest.getColumns().add(prodIdCol);

        CreateTableRequest.ColumnDefinition prodNameCol = new CreateTableRequest.ColumnDefinition();
        prodNameCol.setName("name");
        prodNameCol.setDataType(TableColumn.DataType.VARCHAR);
        prodNameCol.setMaxLength(100);
        prodRequest.getColumns().add(prodNameCol);

        CreateTableRequest.ColumnDefinition catIdRefCol = new CreateTableRequest.ColumnDefinition();
        catIdRefCol.setName("category_id");
        catIdRefCol.setDataType(TableColumn.DataType.INTEGER);
        prodRequest.getColumns().add(catIdRefCol);

        rdbmsService.createTable(prodRequest);

        // Insert test data
        InsertRequest cat1 = new InsertRequest();
        cat1.setTableName("join_categories");
        cat1.setValues(Map.of("id", 1, "name", "Electronics"));
        rdbmsService.insert(cat1);

        InsertRequest cat2 = new InsertRequest();
        cat2.setTableName("join_categories");
        cat2.setValues(Map.of("id", 2, "name", "Books"));
        rdbmsService.insert(cat2);

        InsertRequest prod1 = new InsertRequest();
        prod1.setTableName("join_products");
        prod1.setValues(Map.of("id", 1, "name", "Laptop", "category_id", 1));
        rdbmsService.insert(prod1);

        InsertRequest prod2 = new InsertRequest();
        prod2.setTableName("join_products");
        prod2.setValues(Map.of("id", 2, "name", "Novel", "category_id", 2));
        rdbmsService.insert(prod2);
    }
}
