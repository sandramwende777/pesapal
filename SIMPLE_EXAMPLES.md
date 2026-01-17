# Simple Code Examples: See It in Action

## Example 1: The Simplest Possible Flow

### What Happens When You Create a Table

```java
// STEP 1: User sends HTTP request
POST /api/rdbms/tables
{
  "tableName": "products"
}

// STEP 2: Controller receives it
@RestController
public class RdbmsController {
    @PostMapping("/tables")
    public DatabaseTable createTable(@RequestBody CreateTableRequest request) {
        // request.tableName = "products"
        return rdbmsService.createTable(request);
    }
}

// STEP 3: Service does the work
@Service
public class RdbmsService {
    public DatabaseTable createTable(CreateTableRequest request) {
        // Create a new table entity
        DatabaseTable table = new DatabaseTable();
        table.setTableName("products");
        
        // Save it using repository
        return tableRepository.save(table);
    }
}

// STEP 4: Repository saves to database
public interface DatabaseTableRepository extends JpaRepository<DatabaseTable, Long> {
    // Spring Boot automatically provides save() method
    // It does: INSERT INTO database_tables (table_name) VALUES ('products')
}

// STEP 5: Response goes back
// Database → Repository → Service → Controller → User
// Returns: { "id": 1, "tableName": "products" }
```

---

## Example 2: Understanding Annotations

### @Entity - "This is Important Data"

```java
// WITHOUT @Entity
public class DatabaseTable {
    private String tableName;
}
// Spring Boot: "I don't care about this"

// WITH @Entity
@Entity
public class DatabaseTable {
    private String tableName;
}
// Spring Boot: "Oh! This is important! I'll create a database table for this!"
```

**What Spring Boot does:**
- Creates a table called `database_tables`
- Creates a column called `table_name`
- Automatically!

### @Service - "This Does Business Logic"

```java
// WITHOUT @Service
public class RdbmsService {
    public void createTable() { ... }
}
// Spring Boot: "I don't know about this class"

// WITH @Service
@Service
public class RdbmsService {
    public void createTable() { ... }
}
// Spring Boot: "Oh! This is a service! I'll manage it and inject dependencies!"
```

**What Spring Boot does:**
- Creates an instance of this class
- Injects dependencies (like repositories)
- Makes it available to controllers

### @RestController - "This Receives HTTP Requests"

```java
// WITHOUT @RestController
public class RdbmsController {
    public DatabaseTable createTable() { ... }
}
// Spring Boot: "I don't know this exists"

// WITH @RestController
@RestController
public class RdbmsController {
    @PostMapping("/tables")
    public DatabaseTable createTable() { ... }
}
// Spring Boot: "Oh! When someone sends POST /tables, call this method!"
```

---

## Example 3: Repository Magic

### What You Write

```java
public interface DatabaseTableRepository extends JpaRepository<DatabaseTable, Long> {
    Optional<DatabaseTable> findByTableName(String tableName);
}
```

### What Spring Boot Creates (Behind the Scenes)

```java
// Spring Boot automatically creates this class for you!
public class DatabaseTableRepositoryImpl implements DatabaseTableRepository {
    
    public Optional<DatabaseTable> findByTableName(String tableName) {
        // Spring Boot automatically writes this SQL:
        // SELECT * FROM database_tables WHERE table_name = ?
        // And executes it!
        return database.query("SELECT * FROM database_tables WHERE table_name = ?", tableName);
    }
    
    // Plus all these methods for FREE:
    public DatabaseTable save(DatabaseTable table) { ... }
    public Optional<DatabaseTable> findById(Long id) { ... }
    public List<DatabaseTable> findAll() { ... }
    public void delete(DatabaseTable table) { ... }
}
```

**You write 1 line, Spring Boot creates 100+ lines of code!**

---

## Example 4: Dependency Injection

### The Old Way (Without Spring Boot)

```java
public class RdbmsService {
    private DatabaseTableRepository repository;
    
    public RdbmsService() {
        // You have to create it yourself
        this.repository = new DatabaseTableRepositoryImpl();
    }
}
```

**Problems:**
- You have to create everything yourself
- Hard to test
- Hard to change

### The Spring Boot Way

```java
@Service
@RequiredArgsConstructor
public class RdbmsService {
    private final DatabaseTableRepository repository;
    // Spring Boot automatically creates and gives you the repository!
}
```

**Benefits:**
- Spring Boot creates it for you
- Easy to test (can inject fake repositories)
- Easy to change

**How it works:**
1. Spring Boot sees `@Service`
2. Spring Boot sees `private final DatabaseTableRepository`
3. Spring Boot creates a `DatabaseTableRepository`
4. Spring Boot gives it to `RdbmsService`
5. Magic! ✨

---

## Example 5: Complete Request Flow

### The Journey of a Request

```
1. USER TYPES IN BROWSER:
   "CREATE TABLE products (id INTEGER)"

2. FRONTEND SENDS:
   POST http://localhost:8080/api/rdbms/sql
   { "sql": "CREATE TABLE products (id INTEGER)" }

3. CONTROLLER RECEIVES:
   @RestController
   public class RdbmsController {
       @PostMapping("/sql")
       public Object executeSql(@RequestBody SqlRequest request) {
           // request.sql = "CREATE TABLE products (id INTEGER)"
           return sqlParserService.executeSql(request.getSql());
       }
   }

4. SQL PARSER PARSES:
   @Service
   public class SqlParserService {
       public Object executeSql(String sql) {
           // Parses: "CREATE TABLE products (id INTEGER)"
           // Creates: CreateTableRequest
           CreateTableRequest request = new CreateTableRequest();
           request.setTableName("products");
           // ... parse columns ...
           
           return rdbmsService.createTable(request);
       }
   }

5. SERVICE CREATES TABLE:
   @Service
   public class RdbmsService {
       public DatabaseTable createTable(CreateTableRequest request) {
           // Create entity
           DatabaseTable table = new DatabaseTable();
           table.setTableName("products");
           
           // Save it
           return tableRepository.save(table);
       }
   }

6. REPOSITORY SAVES:
   public interface DatabaseTableRepository extends JpaRepository<...> {
       // Spring Boot automatically provides save() method
   }
   // Executes: INSERT INTO database_tables (table_name) VALUES ('products')

7. DATABASE STORES:
   database_tables table:
   | id | table_name |
   |----|------------|
   | 1  | products   |

8. RESPONSE GOES BACK:
   Database → Repository → Service → Parser → Controller → Frontend → User
   Returns: { "id": 1, "tableName": "products", ... }
```

---

## Example 6: Understanding Our Project Structure

### The Files and What They Do

```
backend/src/main/java/com/pesapal/rdbms/
│
├── entity/                    ← Recipe Cards (Describe Data)
│   ├── DatabaseTable.java     → "A table has: id, name, columns"
│   ├── TableColumn.java       → "A column has: name, type, nullable"
│   └── TableKey.java          → "A key has: table, column, type"
│
├── repository/                ← Pantry Keepers (Get/Save Data)
│   ├── DatabaseTableRepository.java
│   │   → "I can save/get DatabaseTable entities"
│   └── TableColumnRepository.java
│       → "I can save/get TableColumn entities"
│
├── service/                   ← Chefs (Do Business Logic)
│   ├── RdbmsService.java
│   │   → "I create tables, insert data, select data"
│   └── SqlParserService.java
│       → "I parse SQL and call RdbmsService"
│
└── controller/               ← Reception (Receive HTTP Requests)
    └── RdbmsController.java
        → "I receive HTTP requests and call services"
```

---

## Example 7: Why We Store User Tables as Data

### What We CAN'T Do

```java
// User creates table "products" at runtime
// We can't do this:

@Entity
public class Products {  // ❌ ERROR! This doesn't exist at compile time!
    @Id
    private Long id;
    private String name;
}
```

**Why?**
- User creates "products" table when app is running
- We write code BEFORE app runs
- We don't know about "products" when we compile!

### What We DO Instead

```java
// We store it as DATA:

@Entity
public class DatabaseTable {  // ✅ This exists at compile time!
    @Id
    private Long id;
    private String tableName;  // "products" stored here
    
    @OneToMany
    private List<TableColumn> columns;  // Column definitions stored here
}

// When user creates "products" table:
DatabaseTable table = new DatabaseTable();
table.setTableName("products");  // Store as data!
tableRepository.save(table);    // Save to database
```

**The Difference:**
- `DatabaseTable` entity = We know about this (static)
- "products" table = User creates this (dynamic)
- We store dynamic things as data in static entities!

---

## Example 8: Real Code from Our Project

### Creating a Table - Step by Step

```java
// 1. User sends request
POST /api/rdbms/tables
{
  "tableName": "products",
  "columns": [
    {"name": "id", "dataType": "INTEGER"},
    {"name": "name", "dataType": "VARCHAR", "maxLength": 100}
  ],
  "primaryKeys": ["id"]
}

// 2. Controller receives
@RestController
@RequestMapping("/api/rdbms")
public class RdbmsController {
    private final RdbmsService rdbmsService;
    
    @PostMapping("/tables")
    public DatabaseTable createTable(@RequestBody CreateTableRequest request) {
        return rdbmsService.createTable(request);
    }
}

// 3. Service creates table
@Service
public class RdbmsService {
    private final DatabaseTableRepository tableRepository;
    private final TableColumnRepository columnRepository;
    private final TableKeyRepository keyRepository;
    
    public DatabaseTable createTable(CreateTableRequest request) {
        // Create table entity
        DatabaseTable table = new DatabaseTable();
        table.setTableName(request.getTableName());
        table = tableRepository.save(table);  // Save to database
        
        // Create columns
        for (ColumnDefinition colDef : request.getColumns()) {
            TableColumn column = new TableColumn();
            column.setTable(table);
            column.setColumnName(colDef.getName());
            column.setDataType(colDef.getDataType());
            columnRepository.save(column);  // Save each column
        }
        
        // Create primary keys
        for (String pkColumn : request.getPrimaryKeys()) {
            TableKey key = new TableKey();
            key.setTable(table);
            key.setColumnName(pkColumn);
            key.setKeyType(TableKey.KeyType.PRIMARY);
            keyRepository.save(key);  // Save key constraint
        }
        
        return table;
    }
}

// 4. Database stores everything
// database_tables: id=1, table_name='products'
// table_columns: id=1, table_id=1, column_name='id', data_type='INTEGER'
// table_columns: id=2, table_id=1, column_name='name', data_type='VARCHAR'
// table_keys: id=1, table_id=1, column_name='id', key_type='PRIMARY'
```

---

## Example 9: The Magic of Spring Boot

### What You Write

```java
@Service
@RequiredArgsConstructor
public class RdbmsService {
    private final DatabaseTableRepository tableRepository;
    
    public DatabaseTable createTable(String tableName) {
        DatabaseTable table = new DatabaseTable();
        table.setTableName(tableName);
        return tableRepository.save(table);
    }
}
```

### What Spring Boot Does (Automatically!)

1. **Creates DatabaseTableRepository**
   - Spring Boot sees you need it
   - Creates an implementation
   - Gives it to you

2. **Creates RdbmsService**
   - Spring Boot sees `@Service`
   - Creates an instance
   - Injects the repository

3. **Makes It Available**
   - Other classes can use it
   - Controllers can inject it

4. **Manages Everything**
   - Creates objects when needed
   - Destroys them when done
   - Handles errors

**You write 10 lines, Spring Boot does 1000+ lines of work!**

---

## Example 10: Testing Your Understanding

### Question 1: What does @Entity do?

**Answer:**
- Tells Spring Boot: "This class represents data to store in database"
- Spring Boot creates a database table from it
- Like a recipe card that describes what data looks like

### Question 2: What does a Repository do?

**Answer:**
- Gets data from database
- Saves data to database
- Like a pantry keeper who knows where everything is

### Question 3: What does a Service do?

**Answer:**
- Contains business logic
- Does the actual work
- Uses repositories to get/save data
- Like a chef who cooks the food

### Question 4: What does a Controller do?

**Answer:**
- Receives HTTP requests
- Calls services to do work
- Returns responses
- Like a reception desk that takes orders

### Question 5: Why can't we use @Entity for user-created tables?

**Answer:**
- User tables are created at runtime (dynamic)
- @Entity classes must exist at compile time (static)
- So we store user tables as data in static entities

---

## Quick Quiz

1. **What annotation marks a class as a database table?**
   - Answer: `@Entity`

2. **What does `extends JpaRepository` give you?**
   - Answer: Free methods like save(), findById(), findAll(), delete()

3. **What is dependency injection?**
   - Answer: Spring Boot automatically gives you what you need (repositories, services)

4. **What's the difference between @Service and @RestController?**
   - Answer: @Service = business logic, @RestController = receives HTTP requests

5. **Why do we have separate layers (Controller, Service, Repository)?**
   - Answer: Each has a specific job, makes code easier to understand and test

---

## Remember

- **Entity** = Recipe card (describes data)
- **Repository** = Pantry keeper (gets/saves data)
- **Service** = Chef (does business logic)
- **Controller** = Reception (receives requests)
- **Spring Boot** = Magic helper (does a lot of work for you!)

Annotations are like labels that tell Spring Boot what to do!
