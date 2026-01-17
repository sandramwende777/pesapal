package com.pesapal.rdbms.config;

import com.pesapal.rdbms.dto.CreateTableRequest;
import com.pesapal.rdbms.dto.InsertRequest;
import com.pesapal.rdbms.service.FileBasedRdbmsService;
import com.pesapal.rdbms.storage.DataType;
import com.pesapal.rdbms.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Initializes sample e-commerce tables using our file-based RDBMS.
 * 
 * This demonstrates that our RDBMS works by creating tables and
 * inserting sample data entirely through our own storage layer,
 * NOT through JPA or any existing database.
 */
@Component
@Order(1)  // Run early
@RequiredArgsConstructor
@Slf4j
public class FileBasedDataInitializer implements CommandLineRunner {
    
    private final FileBasedRdbmsService rdbmsService;
    private final FileStorageService storage;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing sample data using file-based RDBMS...");
        
        // Only initialize if tables don't exist
        if (!storage.tableExists("products")) {
            initializeEcommerceTables();
        } else {
            log.info("Tables already exist, skipping initialization");
        }
    }
    
    private void initializeEcommerceTables() {
        try {
            // Create Categories table
            createCategoriesTable();
            insertSampleCategories();
            
            // Create Products table
            createProductsTable();
            insertSampleProducts();
            
            // Create Orders table
            createOrdersTable();
            
            // Create Order Items table
            createOrderItemsTable();
            
            log.info("Sample e-commerce tables initialized successfully!");
            
        } catch (Exception e) {
            log.error("Error initializing sample tables", e);
        }
    }
    
    private void createCategoriesTable() {
        CreateTableRequest request = new CreateTableRequest();
        request.setTableName("categories");
        request.setColumns(new ArrayList<>());
        request.setPrimaryKeys(new ArrayList<>());
        request.setUniqueKeys(new ArrayList<>());
        request.setIndexes(new ArrayList<>());
        
        request.getColumns().add(createColumn("id", DataType.INTEGER, null, false));
        request.getColumns().add(createColumn("name", DataType.VARCHAR, 100, false));
        request.getColumns().add(createColumn("description", DataType.TEXT, null, true));
        
        request.getPrimaryKeys().add("id");
        request.getUniqueKeys().add("name");
        
        rdbmsService.createTable(request);
        log.info("Created categories table");
    }
    
    private void createProductsTable() {
        CreateTableRequest request = new CreateTableRequest();
        request.setTableName("products");
        request.setColumns(new ArrayList<>());
        request.setPrimaryKeys(new ArrayList<>());
        request.setUniqueKeys(new ArrayList<>());
        request.setIndexes(new ArrayList<>());
        
        request.getColumns().add(createColumn("id", DataType.INTEGER, null, false));
        request.getColumns().add(createColumn("name", DataType.VARCHAR, 200, false));
        request.getColumns().add(createColumn("description", DataType.TEXT, null, true));
        request.getColumns().add(createColumn("price", DataType.DECIMAL, null, false));
        request.getColumns().add(createColumn("stock", DataType.INTEGER, null, false));
        request.getColumns().add(createColumn("category_id", DataType.INTEGER, null, true));
        
        request.getPrimaryKeys().add("id");
        
        // Add index on category_id for faster lookups
        CreateTableRequest.IndexDefinition categoryIndex = new CreateTableRequest.IndexDefinition();
        categoryIndex.setIndexName("idx_product_category");
        categoryIndex.setColumnName("category_id");
        categoryIndex.setUnique(false);
        request.getIndexes().add(categoryIndex);
        
        rdbmsService.createTable(request);
        log.info("Created products table with index on category_id");
    }
    
    private void createOrdersTable() {
        CreateTableRequest request = new CreateTableRequest();
        request.setTableName("orders");
        request.setColumns(new ArrayList<>());
        request.setPrimaryKeys(new ArrayList<>());
        request.setUniqueKeys(new ArrayList<>());
        request.setIndexes(new ArrayList<>());
        
        request.getColumns().add(createColumn("id", DataType.INTEGER, null, false));
        request.getColumns().add(createColumn("customer_id", DataType.INTEGER, null, false));
        request.getColumns().add(createColumn("order_date", DataType.DATE, null, false));
        request.getColumns().add(createColumn("total_amount", DataType.DECIMAL, null, false));
        request.getColumns().add(createColumn("status", DataType.VARCHAR, 50, false));
        
        request.getPrimaryKeys().add("id");
        
        // Add index on customer_id
        CreateTableRequest.IndexDefinition customerIndex = new CreateTableRequest.IndexDefinition();
        customerIndex.setIndexName("idx_order_customer");
        customerIndex.setColumnName("customer_id");
        customerIndex.setUnique(false);
        request.getIndexes().add(customerIndex);
        
        rdbmsService.createTable(request);
        log.info("Created orders table");
    }
    
    private void createOrderItemsTable() {
        CreateTableRequest request = new CreateTableRequest();
        request.setTableName("order_items");
        request.setColumns(new ArrayList<>());
        request.setPrimaryKeys(new ArrayList<>());
        request.setUniqueKeys(new ArrayList<>());
        request.setIndexes(new ArrayList<>());
        
        request.getColumns().add(createColumn("id", DataType.INTEGER, null, false));
        request.getColumns().add(createColumn("order_id", DataType.INTEGER, null, false));
        request.getColumns().add(createColumn("product_id", DataType.INTEGER, null, false));
        request.getColumns().add(createColumn("quantity", DataType.INTEGER, null, false));
        request.getColumns().add(createColumn("price", DataType.DECIMAL, null, false));
        
        request.getPrimaryKeys().add("id");
        
        // Add indexes
        CreateTableRequest.IndexDefinition orderIndex = new CreateTableRequest.IndexDefinition();
        orderIndex.setIndexName("idx_item_order");
        orderIndex.setColumnName("order_id");
        orderIndex.setUnique(false);
        request.getIndexes().add(orderIndex);
        
        CreateTableRequest.IndexDefinition productIndex = new CreateTableRequest.IndexDefinition();
        productIndex.setIndexName("idx_item_product");
        productIndex.setColumnName("product_id");
        productIndex.setUnique(false);
        request.getIndexes().add(productIndex);
        
        rdbmsService.createTable(request);
        log.info("Created order_items table");
    }
    
    private void insertSampleCategories() {
        insertRow("categories", "id", 1, "name", "Electronics", "description", "Electronic devices and gadgets");
        insertRow("categories", "id", 2, "name", "Clothing", "description", "Apparel and fashion items");
        insertRow("categories", "id", 3, "name", "Books", "description", "Books and literature");
        log.info("Inserted sample categories");
    }
    
    private void insertSampleProducts() {
        insertRow("products", "id", 1, "name", "Laptop", "description", "High-performance laptop", 
                  "price", 999.99, "stock", 50, "category_id", 1);
        insertRow("products", "id", 2, "name", "Smartphone", "description", "Latest smartphone model", 
                  "price", 699.99, "stock", 100, "category_id", 1);
        insertRow("products", "id", 3, "name", "T-Shirt", "description", "Cotton t-shirt", 
                  "price", 19.99, "stock", 200, "category_id", 2);
        insertRow("products", "id", 4, "name", "Programming Book", "description", "Learn Java programming", 
                  "price", 49.99, "stock", 30, "category_id", 3);
        log.info("Inserted sample products");
    }
    
    private CreateTableRequest.ColumnDefinition createColumn(
            String name, DataType dataType, Integer maxLength, boolean nullable) {
        CreateTableRequest.ColumnDefinition col = new CreateTableRequest.ColumnDefinition();
        col.setName(name);
        col.setDataType(dataType);
        col.setMaxLength(maxLength);
        col.setNullable(nullable);
        return col;
    }
    
    private void insertRow(String tableName, Object... keyValuePairs) {
        InsertRequest request = new InsertRequest();
        request.setTableName(tableName);
        request.setValues(new HashMap<>());
        
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            request.getValues().put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        
        rdbmsService.insert(request);
    }
}
