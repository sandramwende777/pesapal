# Requirements Mapping: How Each Part Answers the Challenge

This document explains how each component of the solution addresses the specific requirements from the interview challenge.

## Challenge Requirements Breakdown

### 1. "Design and implement a simple relational database management system (RDBMS)"

**Requirement**: Build a database system that can manage relational data.

**How it's answered**:
- **Location**: `backend/src/main/java/com/pesapal/rdbms/service/RdbmsService.java`
- **Implementation**: The `RdbmsService` class is the core RDBMS engine that:
  - Manages table metadata (structure, columns, types)
  - Stores and retrieves row data
  - Enforces constraints and relationships
  - Performs relational operations (JOINs)
- **Entities**: The JPA entities (`DatabaseTable`, `TableColumn`, `TableRow`, etc.) represent the RDBMS metadata structure

---

### 2. "Support for declaring tables with a few column data types"

**Requirement**: Ability to create tables with different data types.

**How it's answered**:
- **Location**: 
  - `backend/src/main/java/com/pesapal/rdbms/entity/TableColumn.java` (DataType enum)
  - `backend/src/main/java/com/pesapal/rdbms/service/RdbmsService.java` (createTable method)
  - `backend/src/main/java/com/pesapal/rdbms/service/SqlParserService.java` (parseCreateTable method)

**Supported Data Types**:
```java
public enum DataType {
    VARCHAR,    // Variable-length strings
    INTEGER,    // Whole numbers
    BIGINT,     // Large integers
    DECIMAL,    // Decimal numbers
    BOOLEAN,    // True/false values
    DATE,       // Date values
    TIMESTAMP,  // Date and time
    TEXT        // Large text
}
```

**Example Usage**:
```sql
CREATE TABLE products (
    id INTEGER,
    name VARCHAR(100),
    price DECIMAL(10,2),
    description TEXT
);
```

---

### 3. "CRUD operations"

**Requirement**: Create, Read, Update, Delete operations on data.

**How it's answered**:

#### CREATE (Table Creation)
- **Location**: `RdbmsService.createTable()`
- **API**: `POST /api/rdbms/tables`
- **SQL**: `CREATE TABLE ...`

#### READ (SELECT)
- **Location**: `RdbmsService.select()`
- **API**: `POST /api/rdbms/select`
- **SQL**: `SELECT * FROM table WHERE ...`
- **Features**: Supports WHERE clause, LIMIT, OFFSET, column projection

#### UPDATE
- **Location**: `RdbmsService.update()`
- **API**: `PUT /api/rdbms/update`
- **SQL**: `UPDATE table SET ... WHERE ...`
- **Features**: Updates multiple rows based on WHERE conditions

#### DELETE
- **Location**: `RdbmsService.delete()`
- **API**: `DELETE /api/rdbms/delete`
- **SQL**: `DELETE FROM table WHERE ...`
- **Features**: Deletes rows based on WHERE conditions

**Code Example**:
```java
// In RdbmsService.java
@Transactional
public TableRow insert(InsertRequest request) { ... }

@Transactional(readOnly = true)
public List<Map<String, Object>> select(SelectRequest request) { ... }

@Transactional
public int update(UpdateRequest request) { ... }

@Transactional
public int delete(DeleteRequest request) { ... }
```

---

### 4. "Basic indexing"

**Requirement**: Support for indexes to improve query performance.

**How it's answered**:
- **Location**: 
  - `backend/src/main/java/com/pesapal/rdbms/entity/TableIndex.java`
  - `backend/src/main/java/com/pesapal/rdbms/repository/TableIndexRepository.java`
  - `RdbmsService.createTable()` (index creation)

**Features**:
- Indexes can be created on any column
- Support for unique indexes
- Indexes are stored as metadata and can be queried

**Example**:
```sql
CREATE TABLE products (
    id INTEGER,
    category_id INTEGER,
    INDEX idx_category (category_id)
);
```

**Implementation**:
```java
// TableIndex entity stores index metadata
@Entity
public class TableIndex {
    private String indexName;
    private String columnName;
    private Boolean unique;
}
```

---

### 5. "Primary and unique keying"

**Requirement**: Support for primary keys and unique constraints.

**How it's answered**:
- **Location**: 
  - `backend/src/main/java/com/pesapal/rdbms/entity/TableKey.java`
  - `RdbmsService.validatePrimaryKey()` and `validateUniqueKeys()`

**Primary Keys**:
- Enforced uniqueness at application level
- Prevents NULL values
- Validated on INSERT and UPDATE

**Unique Keys**:
- Enforced uniqueness (allows NULL)
- Validated on INSERT and UPDATE

**Example**:
```sql
CREATE TABLE products (
    id INTEGER PRIMARY KEY,
    email VARCHAR(100) UNIQUE
);
```

**Validation Code**:
```java
// In RdbmsService.java
private void validatePrimaryKey(DatabaseTable table, Map<String, Object> rowData) {
    // Checks for duplicate primary key values
    // Ensures primary key is not null
}

private void validateUniqueKeys(DatabaseTable table, Map<String, Object> rowData) {
    // Checks for duplicate unique key values
}
```

---

### 6. "Some joining"

**Requirement**: Support for JOIN operations between tables.

**How it's answered**:
- **Location**: `RdbmsService.join()`
- **API**: `POST /api/rdbms/join`
- **SQL**: `SELECT * FROM table1 JOIN table2 ON ...`

**Supported JOIN Types**:
- **INNER JOIN**: Returns matching rows from both tables
- **LEFT JOIN**: Returns all rows from left table, matching rows from right
- **RIGHT JOIN**: Returns all rows from right table, matching rows from left

**Implementation**:
```java
@Transactional(readOnly = true)
public List<Map<String, Object>> join(JoinRequest request) {
    // Fetches rows from both tables
    // Performs join logic based on join type
    // Returns combined result set
}
```

**Example**:
```sql
SELECT * FROM products p 
INNER JOIN categories c 
ON p.category_id = c.id;
```

---

### 7. "The interface should be SQL or something similar"

**Requirement**: SQL-like interface for interacting with the database.

**How it's answered**:
- **Location**: `backend/src/main/java/com/pesapal/rdbms/service/SqlParserService.java`
- **API**: `POST /api/rdbms/sql`

**Supported SQL Statements**:
- `CREATE TABLE ...`
- `INSERT INTO ... VALUES ...`
- `SELECT ... FROM ... WHERE ...`
- `UPDATE ... SET ... WHERE ...`
- `DELETE FROM ... WHERE ...`
- `SELECT ... FROM ... JOIN ... ON ...`
- `SHOW TABLES`
- `DESCRIBE table_name`

**Parser Implementation**:
```java
public Object executeSql(String sql) {
    // Parses SQL using regex patterns
    // Converts to internal DTOs
    // Calls appropriate RdbmsService methods
    // Returns results
}
```

**Example**:
```bash
POST /api/rdbms/sql
{
  "sql": "SELECT * FROM products WHERE price > 100"
}
```

---

### 8. "Interactive REPL mode"

**Requirement**: Read-Eval-Print Loop for interactive SQL execution.

**How it's answered**:
- **Location**: 
  - Backend: `RdbmsController.executeSql()` endpoint
  - Frontend: `frontend/src/App.js` (SQL REPL tab)

**Backend REPL**:
- REST endpoint accepts SQL strings
- Executes and returns results immediately
- Error handling with clear messages

**Frontend REPL**:
- Interactive textarea for SQL input
- Execute button
- Results displayed in formatted JSON
- Error messages shown clearly

**User Experience**:
1. User types SQL in textarea
2. Clicks "Execute SQL"
3. Query sent to backend
4. Results displayed immediately
5. Can execute next query

---

### 9. "Demonstrate the use of your RDBMS by writing a trivial web app that requires CRUD to the DB"

**Requirement**: Web application that uses the RDBMS for CRUD operations.

**How it's answered**:
- **Location**: `frontend/src/App.js` and related files

**Web App Features**:
1. **SQL REPL Interface**: Execute any SQL query
2. **Table Browser**: View all tables and their data
3. **CRUD Operations**: 
   - Create tables via SQL
   - Read data (SELECT queries)
   - Update data (UPDATE queries)
   - Delete data (DELETE queries)

**CRUD Demonstration**:
- Users can create tables through SQL
- Insert data through SQL or API
- View data in table browser
- Update and delete through SQL
- All operations persist in the RDBMS

---

### 10. "Spring Boot Java using Gradle (not Maven)"

**Requirement**: Spring Boot backend with Gradle build system.

**How it's answered**:
- **Location**: `backend/build.gradle`, `backend/settings.gradle`
- **Build System**: Gradle (not Maven)
- **Dependencies**: Managed through `build.gradle`

**Gradle Configuration**:
```gradle
plugins {
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // ... other dependencies
}
```

---

### 11. "Entities, Services, Repository"

**Requirement**: Spring Boot architecture with these layers.

**How it's answered**:

#### Entities
- **Location**: `backend/src/main/java/com/pesapal/rdbms/entity/`
- **Files**:
  - `DatabaseTable.java` - Table metadata
  - `TableColumn.java` - Column definitions
  - `TableIndex.java` - Index definitions
  - `TableKey.java` - Key constraints
  - `TableRow.java` - Row data storage

#### Repositories
- **Location**: `backend/src/main/java/com/pesapal/rdbms/repository/`
- **Files**:
  - `DatabaseTableRepository.java`
  - `TableColumnRepository.java`
  - `TableIndexRepository.java`
  - `TableKeyRepository.java`
  - `TableRowRepository.java`
- **Technology**: Spring Data JPA

#### Services
- **Location**: `backend/src/main/java/com/pesapal/rdbms/service/`
- **Files**:
  - `RdbmsService.java` - Core RDBMS operations
  - `SqlParserService.java` - SQL parsing and execution

**Architecture Pattern**:
```
Controller → Service → Repository → Entity
```

---

### 12. "Separate web app using React"

**Requirement**: React frontend separate from backend.

**How it's answered**:
- **Location**: `frontend/` directory (completely separate)
- **Technology**: React 18.2.0
- **Communication**: REST API calls to backend
- **Port**: Runs on port 3000 (backend on 8080)

**React Structure**:
```
frontend/
├── src/
│   ├── App.js      # Main component with SQL REPL and table browser
│   ├── App.css     # Styling
│   └── index.js    # Entry point
└── package.json    # React dependencies
```

**Separation**:
- Independent build process (`npm start` vs `./gradlew bootRun`)
- Different ports
- CORS configured for cross-origin requests
- API communication via HTTP

---

### 13. "E-commerce business requirement"

**Requirement**: Demonstrate with e-commerce use case.

**How it's answered**:
- **Location**: `backend/src/main/java/com/pesapal/rdbms/config/DataInitializer.java`

**E-Commerce Tables Created**:
1. **products**: Product catalog
   - id, name, description, price, stock, category_id
   - Primary key: id
   - Index: category_id

2. **categories**: Product categories
   - id, name, description
   - Primary key: id
   - Unique key: name

3. **orders**: Customer orders
   - id, customer_id, order_date, total_amount, status
   - Primary key: id
   - Indexes: customer_id, order_date

4. **order_items**: Order line items
   - id, order_id, product_id, quantity, price
   - Primary key: id
   - Indexes: order_id, product_id

**Sample Data**:
- Pre-populated with sample products, categories
- Demonstrates relationships between tables
- Shows JOIN operations (products JOIN categories)

**Business Logic Demonstration**:
- Products belong to categories (foreign key relationship)
- Orders contain multiple order items
- JOIN queries show product-category relationships
- All CRUD operations work on e-commerce data

---

## Summary: Requirement → Implementation Map

| Requirement | Implementation Location | Key Files |
|------------|------------------------|-----------|
| RDBMS System | Service Layer | `RdbmsService.java` |
| Table Declaration | Entity + Service | `TableColumn.java`, `RdbmsService.createTable()` |
| Data Types | Entity Enum | `TableColumn.DataType` |
| CRUD Operations | Service Methods | `RdbmsService.insert/select/update/delete()` |
| Indexing | Entity + Service | `TableIndex.java`, index creation in `createTable()` |
| Primary/Unique Keys | Entity + Validation | `TableKey.java`, validation methods in `RdbmsService` |
| JOIN Operations | Service Method | `RdbmsService.join()` |
| SQL Interface | Parser Service | `SqlParserService.java` |
| REPL Mode | Controller + Frontend | `RdbmsController.executeSql()`, `App.js` SQL tab |
| Web App | React Frontend | `frontend/src/App.js` |
| Spring Boot + Gradle | Build Config | `backend/build.gradle` |
| Entities | JPA Entities | `entity/` package |
| Repositories | Data Repos | `repository/` package |
| Services | Business Logic | `service/` package |
| React Frontend | UI | `frontend/` directory |
| E-Commerce Demo | Data Initializer | `DataInitializer.java` |

---

## How to Verify Each Requirement

1. **RDBMS**: Start backend, create a table, insert data
2. **Data Types**: Create table with different column types
3. **CRUD**: Execute CREATE, SELECT, UPDATE, DELETE via SQL or API
4. **Indexing**: Create table with indexes, verify in metadata
5. **Keys**: Try inserting duplicate primary/unique keys (should fail)
6. **JOIN**: Execute JOIN query between products and categories
7. **SQL Interface**: Type SQL in frontend REPL, execute
8. **REPL**: Use interactive SQL interface in web app
9. **Web App**: Open `http://localhost:3000`, use UI
10. **E-Commerce**: Check pre-loaded tables (products, categories, etc.)
