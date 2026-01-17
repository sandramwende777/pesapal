package com.pesapal.rdbms.config;

import com.pesapal.rdbms.dto.CreateTableRequest;
import com.pesapal.rdbms.entity.TableColumn;
import com.pesapal.rdbms.repository.DatabaseTableRepository;
import com.pesapal.rdbms.service.RdbmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RdbmsService rdbmsService;
    private final DatabaseTableRepository tableRepository;

    @Override
    public void run(String... args) {
        // Initialize sample e-commerce tables
        initializeEcommerceTables();
    }

    private void initializeEcommerceTables() {
        try {
            // Create Products table
            if (!tableRepository.existsByTableName("products")) {
                CreateTableRequest productsRequest = new CreateTableRequest();
                productsRequest.setTableName("products");
                productsRequest.setColumns(new ArrayList<>());
                productsRequest.setPrimaryKeys(new ArrayList<>());
                productsRequest.setUniqueKeys(new ArrayList<>());
                productsRequest.setIndexes(new ArrayList<>());

                // Product columns
                productsRequest.getColumns().add(createColumn("id", TableColumn.DataType.INTEGER, null, false, null));
                productsRequest.getColumns().add(createColumn("name", TableColumn.DataType.VARCHAR, 200, false, null));
                productsRequest.getColumns().add(createColumn("description", TableColumn.DataType.TEXT, null, true, null));
                productsRequest.getColumns().add(createColumn("price", TableColumn.DataType.DECIMAL, null, false, null));
                productsRequest.getColumns().add(createColumn("stock", TableColumn.DataType.INTEGER, null, false, 0));
                productsRequest.getColumns().add(createColumn("category_id", TableColumn.DataType.INTEGER, null, true, null));

                productsRequest.getPrimaryKeys().add("id");
                productsRequest.getIndexes().add(createIndex("idx_category", "category_id", false));

                rdbmsService.createTable(productsRequest);
                log.info("Created products table");

                // Insert sample products
                insertSampleProducts();
            }

            // Create Categories table
            if (!tableRepository.existsByTableName("categories")) {
                CreateTableRequest categoriesRequest = new CreateTableRequest();
                categoriesRequest.setTableName("categories");
                categoriesRequest.setColumns(new ArrayList<>());
                categoriesRequest.setPrimaryKeys(new ArrayList<>());
                categoriesRequest.setUniqueKeys(new ArrayList<>());

                categoriesRequest.getColumns().add(createColumn("id", TableColumn.DataType.INTEGER, null, false, null));
                categoriesRequest.getColumns().add(createColumn("name", TableColumn.DataType.VARCHAR, 100, false, null));
                categoriesRequest.getColumns().add(createColumn("description", TableColumn.DataType.TEXT, null, true, null));

                categoriesRequest.getPrimaryKeys().add("id");
                categoriesRequest.getUniqueKeys().add("name");

                rdbmsService.createTable(categoriesRequest);
                log.info("Created categories table");

                // Insert sample categories
                insertSampleCategories();
            }

            // Create Orders table
            if (!tableRepository.existsByTableName("orders")) {
                CreateTableRequest ordersRequest = new CreateTableRequest();
                ordersRequest.setTableName("orders");
                ordersRequest.setColumns(new ArrayList<>());
                ordersRequest.setPrimaryKeys(new ArrayList<>());
                ordersRequest.setUniqueKeys(new ArrayList<>());
                ordersRequest.setIndexes(new ArrayList<>());

                ordersRequest.getColumns().add(createColumn("id", TableColumn.DataType.INTEGER, null, false, null));
                ordersRequest.getColumns().add(createColumn("customer_id", TableColumn.DataType.INTEGER, null, false, null));
                ordersRequest.getColumns().add(createColumn("order_date", TableColumn.DataType.DATE, null, false, null));
                ordersRequest.getColumns().add(createColumn("total_amount", TableColumn.DataType.DECIMAL, null, false, null));
                ordersRequest.getColumns().add(createColumn("status", TableColumn.DataType.VARCHAR, 50, false, "pending"));

                ordersRequest.getPrimaryKeys().add("id");
                ordersRequest.getIndexes().add(createIndex("idx_customer", "customer_id", false));
                ordersRequest.getIndexes().add(createIndex("idx_order_date", "order_date", false));

                rdbmsService.createTable(ordersRequest);
                log.info("Created orders table");
            }

            // Create OrderItems table
            if (!tableRepository.existsByTableName("order_items")) {
                CreateTableRequest orderItemsRequest = new CreateTableRequest();
                orderItemsRequest.setTableName("order_items");
                orderItemsRequest.setColumns(new ArrayList<>());
                orderItemsRequest.setPrimaryKeys(new ArrayList<>());
                orderItemsRequest.setUniqueKeys(new ArrayList<>());
                orderItemsRequest.setIndexes(new ArrayList<>());

                orderItemsRequest.getColumns().add(createColumn("id", TableColumn.DataType.INTEGER, null, false, null));
                orderItemsRequest.getColumns().add(createColumn("order_id", TableColumn.DataType.INTEGER, null, false, null));
                orderItemsRequest.getColumns().add(createColumn("product_id", TableColumn.DataType.INTEGER, null, false, null));
                orderItemsRequest.getColumns().add(createColumn("quantity", TableColumn.DataType.INTEGER, null, false, null));
                orderItemsRequest.getColumns().add(createColumn("price", TableColumn.DataType.DECIMAL, null, false, null));

                orderItemsRequest.getPrimaryKeys().add("id");
                orderItemsRequest.getIndexes().add(createIndex("idx_order", "order_id", false));
                orderItemsRequest.getIndexes().add(createIndex("idx_product", "product_id", false));

                rdbmsService.createTable(orderItemsRequest);
                log.info("Created order_items table");
            }

        } catch (Exception e) {
            log.error("Error initializing e-commerce tables", e);
        }
    }

    private CreateTableRequest.ColumnDefinition createColumn(
            String name, TableColumn.DataType dataType, Integer maxLength,
            Boolean nullable, Object defaultValue) {
        CreateTableRequest.ColumnDefinition col = new CreateTableRequest.ColumnDefinition();
        col.setName(name);
        col.setDataType(dataType);
        col.setMaxLength(maxLength);
        col.setNullable(nullable);
        col.setDefaultValue(defaultValue);
        return col;
    }

    private CreateTableRequest.IndexDefinition createIndex(
            String indexName, String columnName, Boolean unique) {
        CreateTableRequest.IndexDefinition idx = new CreateTableRequest.IndexDefinition();
        idx.setIndexName(indexName);
        idx.setColumnName(columnName);
        idx.setUnique(unique);
        return idx;
    }

    private void insertSampleCategories() {
        try {
            rdbmsService.insert(createInsertRequest("categories", "id", 1, "name", "Electronics", "description", "Electronic devices and gadgets"));
            rdbmsService.insert(createInsertRequest("categories", "id", 2, "name", "Clothing", "description", "Apparel and fashion items"));
            rdbmsService.insert(createInsertRequest("categories", "id", 3, "name", "Books", "description", "Books and literature"));
            log.info("Inserted sample categories");
        } catch (Exception e) {
            log.error("Error inserting sample categories", e);
        }
    }

    private void insertSampleProducts() {
        try {
            rdbmsService.insert(createInsertRequest("products", "id", 1, "name", "Laptop", "description", "High-performance laptop", "price", 999.99, "stock", 50, "category_id", 1));
            rdbmsService.insert(createInsertRequest("products", "id", 2, "name", "Smartphone", "description", "Latest smartphone model", "price", 699.99, "stock", 100, "category_id", 1));
            rdbmsService.insert(createInsertRequest("products", "id", 3, "name", "T-Shirt", "description", "Cotton t-shirt", "price", 19.99, "stock", 200, "category_id", 2));
            rdbmsService.insert(createInsertRequest("products", "id", 4, "name", "Programming Book", "description", "Learn Java programming", "price", 49.99, "stock", 30, "category_id", 3));
            log.info("Inserted sample products");
        } catch (Exception e) {
            log.error("Error inserting sample products", e);
        }
    }

    private com.pesapal.rdbms.dto.InsertRequest createInsertRequest(String tableName, Object... keyValuePairs) {
        com.pesapal.rdbms.dto.InsertRequest request = new com.pesapal.rdbms.dto.InsertRequest();
        request.setTableName(tableName);
        request.setValues(new java.util.HashMap<>());
        
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            request.getValues().put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        
        return request;
    }
}
