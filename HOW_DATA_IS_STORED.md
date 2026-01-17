# How Data is Added and Updated in the RDBMS

## Overview

This RDBMS uses a **metadata-based architecture** where:
- **Table schemas** are stored as metadata in JPA entities (`DatabaseTable`, `TableColumn`, etc.)
- **Actual row data** is stored as **JSON strings** in the `TableRow` entity

## Data Storage Architecture

### The TableRow Entity

All data for user-created tables is stored in the `TableRow` entity:

```java
@Entity
@Table(name = "table_rows")
public class TableRow {
    private Long id;                    // Auto-generated row ID
    private DatabaseTable table;        // Which table this row belongs to
    private String rowData;             // JSON string: {"id":1, "name":"Laptop", "price":999.99}
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Key Point**: The `rowData` field stores the entire row as a JSON string, allowing flexible schemas.

### Example Storage

When you insert:
```sql
INSERT INTO products (id, name, price) VALUES (1, 'Laptop', 999.99)
```

The system creates a `TableRow` entity:
- `table` → DatabaseTable("products")
- `rowData` → `"{\"id\":1,\"name\":\"Laptop\",\"price\":999.99}"`

## How INSERT Works

### Step-by-Step Process

1. **Receive Insert Request**
   ```java
   InsertRequest {
       tableName: "products"
       values: {
           "id": 1,
           "name": "Laptop",
           "price": 999.99
       }
   }
   ```

2. **Load Table Metadata**
   - Fetch `DatabaseTable` entity for "products"
   - Load all `TableColumn` entities (defines valid columns)
   - Load all `TableKey` entities (defines primary/unique keys)

3. **Validate Columns Exist**
   ```java
   // Check that all columns in the insert exist in metadata
   Set<String> validColumns = table.getColumns().stream()
       .map(TableColumn::getColumnName)
       .collect(Collectors.toSet());
   
   // Throws error if column doesn't exist
   if (!validColumns.contains(columnName)) {
       throw new IllegalArgumentException("Column not found: " + columnName);
   }
   ```

4. **Validate Constraints**
   - **Primary Key**: Check for duplicates, ensure not null
   - **Unique Keys**: Check for duplicates
   - **Nullable**: Ensure required columns are provided (or use defaults)

5. **Serialize to JSON and Save**
   ```java
   TableRow row = new TableRow();
   row.setTable(table);
   row.setRowData(objectMapper.writeValueAsString(request.getValues()));
   // rowData = "{\"id\":1,\"name\":\"Laptop\",\"price\":999.99}"
   
   return rowRepository.save(row);  // Persist to database
   ```

### Code Location

**File**: `backend/src/main/java/com/pesapal/rdbms/service/RdbmsService.java`
**Method**: `insert(InsertRequest request)` (lines 141-181)

### Example from DataInitializer

```java
// Inserting a product
rdbmsService.insert(createInsertRequest(
    "products", 
    "id", 1, 
    "name", "Laptop", 
    "description", "High-performance laptop", 
    "price", 999.99, 
    "stock", 50, 
    "category_id", 1
));
```

This creates a `TableRow` with:
```json
{
  "id": 1,
  "name": "Laptop",
  "description": "High-performance laptop",
  "price": 999.99,
  "stock": 50,
  "category_id": 1
}
```

## How UPDATE Works

### Step-by-Step Process

1. **Receive Update Request**
   ```java
   UpdateRequest {
       tableName: "products"
       set: {
           "price": 899.99,  // New price
           "stock": 45       // New stock
       }
       where: {
           "id": 1          // Update product with id=1
       }
   }
   ```

2. **Load Table and Rows**
   ```java
   DatabaseTable table = tableRepository.findByTableName("products");
   List<TableRow> rows = rowRepository.findByTable(table);
   ```

3. **Filter Rows (WHERE clause)**
   ```java
   // Apply WHERE clause to find matching rows
   if (request.getWhere() != null) {
       rows = filterRows(rows, request.getWhere());
   }
   // Now rows only contains rows matching WHERE condition
   ```

4. **For Each Matching Row:**
   ```java
   for (TableRow row : rows) {
       // 1. Deserialize JSON to Map
       Map<String, Object> rowData = objectMapper.readValue(
           row.getRowData(),
           new TypeReference<Map<String, Object>>() {}
       );
       // rowData = {"id":1, "name":"Laptop", "price":999.99, ...}
       
       // 2. Update values
       for (Map.Entry<String, Object> entry : request.getSet().entrySet()) {
           rowData.put(entry.getKey(), entry.getValue());
       }
       // rowData = {"id":1, "name":"Laptop", "price":899.99, "stock":45, ...}
       
       // 3. Validate constraints (primary key, unique keys)
       validatePrimaryKey(table, rowData);
       validateUniqueKeys(table, rowData);
       
       // 4. Serialize back to JSON and save
       row.setRowData(objectMapper.writeValueAsString(rowData));
       rowRepository.save(row);
   }
   ```

### Code Location

**File**: `backend/src/main/java/com/pesapal/rdbms/service/RdbmsService.java`
**Method**: `update(UpdateRequest request)` (lines 227-265)

### Example SQL Update

```sql
UPDATE products SET price = 899.99, stock = 45 WHERE id = 1
```

**Process**:
1. Find all `TableRow` entities for "products" table
2. Filter to rows where `rowData` JSON contains `"id":1`
3. Deserialize JSON: `{"id":1, "name":"Laptop", "price":999.99, ...}`
4. Update: `{"id":1, "name":"Laptop", "price":899.99, "stock":45, ...}`
5. Validate constraints
6. Serialize back to JSON and save

## Why JSON Storage?

### Advantages

1. **Dynamic Schemas**: Different tables have different columns
   - Products table: `{id, name, price, stock}`
   - Categories table: `{id, name, description}`
   - We can't create a JPA entity for each user table

2. **Flexibility**: Easy to add/remove columns without schema changes

3. **Validation**: We validate against metadata (`TableColumn` entities) before storing

### Trade-offs

- **Query Performance**: Must deserialize JSON to filter/search
- **Type Safety**: JSON values are `Object`, need type conversion
- **Storage**: JSON strings take more space than typed columns

## Database Storage

### Physical Storage in H2 Database

The `table_rows` table structure:
```sql
CREATE TABLE table_rows (
    id BIGINT PRIMARY KEY,
    table_id BIGINT NOT NULL,           -- Foreign key to database_tables
    row_data TEXT NOT NULL,             -- JSON string
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### Example Row in Database

```
id: 1
table_id: 1 (points to "products" table)
row_data: {"id":1,"name":"Laptop","price":999.99,"stock":50,"category_id":1}
created_at: 2026-01-17 12:24:19
updated_at: 2026-01-17 12:24:19
```

## Validation Process

### Primary Key Validation

```java
private void validatePrimaryKey(DatabaseTable table, Map<String, Object> rowData) {
    // 1. Get primary key columns from metadata
    List<TableKey> primaryKeys = table.getKeys().stream()
        .filter(k -> k.getKeyType() == TableKey.KeyType.PRIMARY)
        .collect(Collectors.toList());
    
    // 2. Check primary key is not null
    Object value = rowData.get(pk.getColumnName());
    if (value == null) {
        throw new IllegalArgumentException("Primary key cannot be null");
    }
    
    // 3. Check for duplicates
    // Load all existing rows, deserialize JSON, check for duplicate PK
    List<TableRow> existingRows = rowRepository.findByTable(table);
    for (TableRow existingRow : existingRows) {
        Map<String, Object> existingData = deserialize(existingRow.getRowData());
        if (existingData.get(pk.getColumnName()).equals(value)) {
            throw new IllegalArgumentException("Duplicate primary key");
        }
    }
}
```

### Unique Key Validation

Similar process - checks all existing rows for duplicate unique key values.

### Nullable Validation

```java
for (TableColumn column : table.getColumns()) {
    if (!column.getNullable() && !request.getValues().containsKey(column.getColumnName())) {
        if (column.getDefaultValue() == null) {
            throw new IllegalArgumentException("Column is not nullable");
        }
        // Use default value if provided
    }
}
```

## Summary

1. **Data Storage**: All row data stored as JSON strings in `TableRow.rowData`
2. **INSERT**: Validate against metadata → Serialize to JSON → Save `TableRow`
3. **UPDATE**: Load rows → Filter (WHERE) → Deserialize JSON → Update values → Validate → Serialize → Save
4. **Validation**: Uses metadata entities (`TableColumn`, `TableKey`) to validate constraints
5. **Flexibility**: JSON allows dynamic schemas while maintaining data integrity through metadata validation
