package com.pesapal.rdbms;

import com.pesapal.rdbms.dto.CreateTableRequest;
import com.pesapal.rdbms.dto.InsertRequest;
import com.pesapal.rdbms.dto.SelectRequest;
import com.pesapal.rdbms.service.FileBasedRdbmsService;
import com.pesapal.rdbms.storage.DataType;
import com.pesapal.rdbms.storage.index.IndexManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark test to demonstrate that indexes actually improve query performance.
 * 
 * This test:
 * 1. Creates a table with an indexed column
 * 2. Inserts a large number of rows
 * 3. Queries WITH index (fast)
 * 4. Queries WITHOUT index (full scan)
 * 5. Compares the performance
 */
@SpringBootTest
public class IndexBenchmarkTest {
    
    @Autowired
    private FileBasedRdbmsService rdbmsService;
    
    @Autowired
    private IndexManager indexManager;
    
    private static final int NUM_ROWS = 10000;
    private static final String TABLE_NAME = "benchmark_test";
    
    @BeforeEach
    void setup() {
        // Clean up if table exists
        try {
            rdbmsService.dropTable(TABLE_NAME);
        } catch (Exception ignored) {}
        
        // Create test table with indexed column
        CreateTableRequest createRequest = new CreateTableRequest();
        createRequest.setTableName(TABLE_NAME);
        createRequest.setColumns(new ArrayList<>());
        createRequest.setPrimaryKeys(new ArrayList<>());
        createRequest.setIndexes(new ArrayList<>());
        
        // Add columns
        CreateTableRequest.ColumnDefinition idCol = new CreateTableRequest.ColumnDefinition();
        idCol.setName("id");
        idCol.setDataType(DataType.INTEGER);
        idCol.setNullable(false);
        createRequest.getColumns().add(idCol);
        
        CreateTableRequest.ColumnDefinition categoryCol = new CreateTableRequest.ColumnDefinition();
        categoryCol.setName("category_id");
        categoryCol.setDataType(DataType.INTEGER);
        categoryCol.setNullable(false);
        createRequest.getColumns().add(categoryCol);
        
        CreateTableRequest.ColumnDefinition nameCol = new CreateTableRequest.ColumnDefinition();
        nameCol.setName("name");
        nameCol.setDataType(DataType.VARCHAR);
        nameCol.setMaxLength(100);
        createRequest.getColumns().add(nameCol);
        
        CreateTableRequest.ColumnDefinition priceCol = new CreateTableRequest.ColumnDefinition();
        priceCol.setName("price");
        priceCol.setDataType(DataType.DECIMAL);
        createRequest.getColumns().add(priceCol);
        
        // Primary key on id
        createRequest.getPrimaryKeys().add("id");
        
        // Index on category_id (this is what we'll benchmark)
        CreateTableRequest.IndexDefinition categoryIndex = new CreateTableRequest.IndexDefinition();
        categoryIndex.setIndexName("idx_category");
        categoryIndex.setColumnName("category_id");
        categoryIndex.setUnique(false);
        createRequest.getIndexes().add(categoryIndex);
        
        // Index on price for range queries
        CreateTableRequest.IndexDefinition priceIndex = new CreateTableRequest.IndexDefinition();
        priceIndex.setIndexName("idx_price");
        priceIndex.setColumnName("price");
        priceIndex.setUnique(false);
        createRequest.getIndexes().add(priceIndex);
        
        rdbmsService.createTable(createRequest);
        
        System.out.println("\n========================================");
        System.out.println("INDEX BENCHMARK TEST");
        System.out.println("========================================");
        System.out.println("Inserting " + NUM_ROWS + " rows...");
        
        // Insert test data
        long insertStart = System.currentTimeMillis();
        Random random = new Random(42);
        for (int i = 1; i <= NUM_ROWS; i++) {
            InsertRequest insertRequest = new InsertRequest();
            insertRequest.setTableName(TABLE_NAME);
            insertRequest.setValues(new HashMap<>());
            insertRequest.getValues().put("id", i);
            insertRequest.getValues().put("category_id", random.nextInt(100) + 1);  // 1-100
            insertRequest.getValues().put("name", "Product " + i);
            insertRequest.getValues().put("price", random.nextDouble() * 1000);  // 0-1000
            rdbmsService.insert(insertRequest);
            
            if (i % 1000 == 0) {
                System.out.println("  Inserted " + i + " rows...");
            }
        }
        long insertDuration = System.currentTimeMillis() - insertStart;
        System.out.println("Insert complete in " + insertDuration + " ms");
        System.out.println("----------------------------------------\n");
    }
    
    @Test
    void testIndexedQueryPerformance() {
        System.out.println("TEST 1: Indexed Column Query (category_id = 50)");
        System.out.println("------------------------------------------------");
        
        // Query on indexed column
        SelectRequest selectRequest = new SelectRequest();
        selectRequest.setTableName(TABLE_NAME);
        selectRequest.setWhere(new HashMap<>());
        selectRequest.getWhere().put("category_id", 50);
        
        // Warm up
        rdbmsService.select(selectRequest);
        
        // Benchmark
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> results = rdbmsService.select(selectRequest);
        long duration = System.currentTimeMillis() - startTime;
        
        var execution = rdbmsService.getLastQueryExecution();
        
        System.out.println("Results: " + results.size() + " rows");
        System.out.println("Index Used: " + execution.isIndexUsed());
        if (execution.isIndexUsed()) {
            System.out.println("Index Name: " + execution.getIndexName());
            System.out.println("Operation: " + execution.getIndexOperation());
        }
        System.out.println("Execution Time: " + duration + " ms");
        System.out.println();
        
        assertTrue(execution.isIndexUsed(), "Index should be used for category_id query");
    }
    
    @Test
    void testNonIndexedQueryPerformance() {
        System.out.println("TEST 2: Non-Indexed Column Query (name LIKE '%Product 500%')");
        System.out.println("-------------------------------------------------------------");
        
        // Query on non-indexed column (LIKE can't use index efficiently)
        SelectRequest selectRequest = new SelectRequest();
        selectRequest.setTableName(TABLE_NAME);
        selectRequest.setWhere(new HashMap<>());
        Map<String, Object> likeCondition = new HashMap<>();
        likeCondition.put("op", "LIKE");
        likeCondition.put("value", "%Product 500%");
        selectRequest.getWhere().put("name", likeCondition);
        
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> results = rdbmsService.select(selectRequest);
        long duration = System.currentTimeMillis() - startTime;
        
        var execution = rdbmsService.getLastQueryExecution();
        
        System.out.println("Results: " + results.size() + " rows");
        System.out.println("Index Used: " + execution.isIndexUsed());
        System.out.println("Rows Scanned: " + execution.getRowsScanned());
        System.out.println("Execution Time: " + duration + " ms");
        System.out.println();
        
        assertFalse(execution.isIndexUsed(), "LIKE query should not use index");
    }
    
    @Test
    void testRangeQueryPerformance() {
        System.out.println("TEST 3: Range Query (price > 900)");
        System.out.println("----------------------------------");
        
        // Range query on indexed column
        SelectRequest selectRequest = new SelectRequest();
        selectRequest.setTableName(TABLE_NAME);
        selectRequest.setWhere(new HashMap<>());
        Map<String, Object> rangeCondition = new HashMap<>();
        rangeCondition.put("op", ">");
        rangeCondition.put("value", 900);
        selectRequest.getWhere().put("price", rangeCondition);
        
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> results = rdbmsService.select(selectRequest);
        long duration = System.currentTimeMillis() - startTime;
        
        var execution = rdbmsService.getLastQueryExecution();
        
        System.out.println("Results: " + results.size() + " rows");
        System.out.println("Index Used: " + execution.isIndexUsed());
        if (execution.isIndexUsed()) {
            System.out.println("Index Name: " + execution.getIndexName());
            System.out.println("Operation: " + execution.getIndexOperation());
        }
        System.out.println("Execution Time: " + duration + " ms");
        System.out.println();
        
        assertTrue(execution.isIndexUsed(), "Range query should use index");
        assertEquals("RANGE_SCAN_GT", execution.getIndexOperation(), 
                     "Should use RANGE_SCAN_GT operation");
    }
    
    @Test
    void testPrimaryKeyLookup() {
        System.out.println("TEST 4: Primary Key Lookup (id = 5000)");
        System.out.println("--------------------------------------");
        
        // Primary key lookup (should be fastest)
        SelectRequest selectRequest = new SelectRequest();
        selectRequest.setTableName(TABLE_NAME);
        selectRequest.setWhere(new HashMap<>());
        selectRequest.getWhere().put("id", 5000);
        
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> results = rdbmsService.select(selectRequest);
        long duration = System.currentTimeMillis() - startTime;
        
        var execution = rdbmsService.getLastQueryExecution();
        
        System.out.println("Results: " + results.size() + " rows");
        System.out.println("Index Used: " + execution.isIndexUsed());
        if (execution.isIndexUsed()) {
            System.out.println("Index Name: " + execution.getIndexName());
            System.out.println("Operation: " + execution.getIndexOperation());
        }
        System.out.println("Execution Time: " + duration + " ms");
        System.out.println();
        
        assertEquals(1, results.size(), "Should find exactly one row");
        assertTrue(execution.isIndexUsed(), "Primary key lookup should use index");
    }
    
    @Test
    void testIndexStatistics() {
        System.out.println("TEST 5: Index Statistics");
        System.out.println("------------------------");
        
        Map<String, Object> stats = rdbmsService.getIndexStats();
        
        System.out.println("Index Lookups Used: " + stats.get("indexLookupsUsed"));
        System.out.println("Full Table Scans: " + stats.get("fullTableScans"));
        System.out.println();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> tableStats = (Map<String, Object>) stats.get("tables");
        if (tableStats != null && tableStats.containsKey(TABLE_NAME)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> benchmarkStats = (Map<String, Object>) tableStats.get(TABLE_NAME);
            System.out.println("Table: " + TABLE_NAME);
            System.out.println("Indexed Columns: " + benchmarkStats.get("indexedColumns"));
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> indexes = (List<Map<String, Object>>) benchmarkStats.get("indexes");
            for (Map<String, Object> idx : indexes) {
                System.out.println("  Index: " + idx.get("indexName"));
                System.out.println("    Keys: " + idx.get("keyCount"));
                System.out.println("    Entries: " + idx.get("entryCount"));
                System.out.println("    Lookups: " + idx.get("lookupCount"));
            }
        }
    }
    
    @Test
    void testCompareIndexedVsNonIndexed() {
        System.out.println("\n========================================");
        System.out.println("PERFORMANCE COMPARISON");
        System.out.println("========================================\n");
        
        // Test 1: Query with index
        SelectRequest indexedQuery = new SelectRequest();
        indexedQuery.setTableName(TABLE_NAME);
        indexedQuery.setWhere(new HashMap<>());
        indexedQuery.getWhere().put("category_id", 25);
        
        // Warm up
        for (int i = 0; i < 3; i++) {
            rdbmsService.select(indexedQuery);
        }
        
        // Benchmark indexed query
        long indexedStart = System.currentTimeMillis();
        int iterations = 100;
        int indexedResultCount = 0;
        for (int i = 0; i < iterations; i++) {
            indexedQuery.getWhere().put("category_id", (i % 100) + 1);
            indexedResultCount = rdbmsService.select(indexedQuery).size();
        }
        long indexedDuration = System.currentTimeMillis() - indexedStart;
        
        System.out.println("INDEXED QUERIES (category_id = X):");
        System.out.println("  Iterations: " + iterations);
        System.out.println("  Total Time: " + indexedDuration + " ms");
        System.out.println("  Avg Time: " + (indexedDuration / iterations) + " ms per query");
        System.out.println();
        
        // Test 2: Query without index (full scan)
        SelectRequest fullScanQuery = new SelectRequest();
        fullScanQuery.setTableName(TABLE_NAME);
        fullScanQuery.setWhere(new HashMap<>());
        Map<String, Object> likeCondition = new HashMap<>();
        likeCondition.put("op", "LIKE");
        likeCondition.put("value", "%Product 1%");
        fullScanQuery.getWhere().put("name", likeCondition);
        
        // Benchmark full scan query
        long fullScanStart = System.currentTimeMillis();
        int fullScanResultCount = 0;
        for (int i = 0; i < iterations; i++) {
            likeCondition.put("value", "%Product " + (i + 1) + "%");
            fullScanResultCount = rdbmsService.select(fullScanQuery).size();
        }
        long fullScanDuration = System.currentTimeMillis() - fullScanStart;
        
        System.out.println("FULL SCAN QUERIES (name LIKE '%..%'):");
        System.out.println("  Iterations: " + iterations);
        System.out.println("  Total Time: " + fullScanDuration + " ms");
        System.out.println("  Avg Time: " + (fullScanDuration / iterations) + " ms per query");
        System.out.println();
        
        // Calculate speedup
        double speedup = (double) fullScanDuration / indexedDuration;
        System.out.println("========================================");
        System.out.println("RESULT: Index queries are " + String.format("%.1f", speedup) + "x faster!");
        System.out.println("========================================\n");
        
        assertTrue(indexedDuration < fullScanDuration, 
                   "Indexed queries should be faster than full scans");
    }
}
