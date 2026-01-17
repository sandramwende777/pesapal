# Architecture Explanation: How Everything Connects

## High-Level Architecture Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    USER INTERACTION                          │
│  (Browser at http://localhost:3000)                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ HTTP Requests
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              REACT FRONTEND (Port 3000)                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  SQL REPL Tab: User types SQL queries                │  │
│  │  Table Browser Tab: Views table data                  │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ REST API Calls (axios)
                       │ POST /api/rdbms/sql
                       │ POST /api/rdbms/select
                       │ etc.
                       ▼
┌─────────────────────────────────────────────────────────────┐
│         SPRING BOOT BACKEND (Port 8080)                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  RdbmsController                                      │  │
│  │  - Receives HTTP requests                            │  │
│  │  - Routes to appropriate service                      │  │
│  └───────────────┬──────────────────────────────────────┘  │
│                  │                                          │
│                  ▼                                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  SqlParserService                                     │  │
│  │  - Parses SQL string                                  │  │
│  │  - Converts to DTOs                                   │  │
│  │  - Calls RdbmsService                                │  │
│  └───────────────┬──────────────────────────────────────┘  │
│                  │                                          │
│                  ▼                                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  RdbmsService (Core RDBMS Engine)                    │  │
│  │  - createTable()    → Creates table metadata         │  │
│  │  - insert()         → Stores row data                │  │
│  │  - select()         → Retrieves rows                 │  │
│  │  - update()         → Modifies rows                  │  │
│  │  - delete()         → Removes rows                   │  │
│  │  - join()           → Performs JOINs                 │  │
│  └───────────────┬──────────────────────────────────────┘  │
│                  │                                          │
│                  ▼                                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Repositories (Spring Data JPA)                      │  │
│  │  - DatabaseTableRepository                           │  │
│  │  - TableColumnRepository                             │  │
│  │  - TableRowRepository                                │  │
│  │  - TableIndexRepository                              │  │
│  │  - TableKeyRepository                                │  │
│  └───────────────┬──────────────────────────────────────┘  │
│                  │                                          │
│                  ▼                                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  JPA Entities (Metadata Storage)                     │  │
│  │  - DatabaseTable  → Table definitions                │  │
│  │  - TableColumn   → Column definitions                │  │
│  │  - TableIndex    → Index definitions                 │  │
│  │  - TableKey      → Key constraints                   │  │
│  │  - TableRow      → Actual row data (as JSON)         │  │
│  └───────────────┬──────────────────────────────────────┘  │
│                  │                                          │
│                  ▼                                          │
└─────────────────────────────────────────────────────────────┘
                       │
                       │ JPA/Hibernate
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              H2 IN-MEMORY DATABASE                           │
│  (Stores the RDBMS metadata - tables, columns, rows)        │
└─────────────────────────────────────────────────────────────┘
```

## How Each Requirement is Fulfilled

### 1. "Simple RDBMS" → RdbmsService

**What it does**: The `RdbmsService` class IS the RDBMS engine. It:
- Manages table structures (metadata)
- Stores and retrieves data
- Enforces constraints
- Performs relational operations

**Key Method**: All RDBMS operations are in `RdbmsService.java`

---

### 2. "Table Declaration with Data Types" → TableColumn Entity + createTable()

**Flow**:
1. User executes: `CREATE TABLE products (id INTEGER, name VARCHAR(100))`
2. `SqlParserService` parses the SQL
3. Creates `CreateTableRequest` DTO with column definitions
4. `RdbmsService.createTable()` processes it
5. Creates `TableColumn` entities with `DataType` enum values
6. Stores in database via `TableColumnRepository`

**Files**:
- `TableColumn.java` - Defines the DataType enum
- `RdbmsService.createTable()` - Creates columns with types

---

### 3. "CRUD Operations" → Service Methods

**CREATE (Table)**:
```
SQL: CREATE TABLE ...
  → SqlParserService.parseCreateTable()
  → RdbmsService.createTable()
  → Saves DatabaseTable + TableColumn entities
```

**INSERT**:
```
SQL: INSERT INTO products VALUES ...
  → SqlParserService.parseInsert()
  → RdbmsService.insert()
  → Validates constraints
  → Creates TableRow with JSON data
```

**SELECT**:
```
SQL: SELECT * FROM products
  → SqlParserService.parseSelect()
  → RdbmsService.select()
  → Retrieves TableRow entities
  → Parses JSON data
  → Returns List<Map<String, Object>>
```

**UPDATE**:
```
SQL: UPDATE products SET price = 100
  → SqlParserService.parseUpdate()
  → RdbmsService.update()
  → Updates TableRow JSON data
```

**DELETE**:
```
SQL: DELETE FROM products WHERE id = 1
  → SqlParserService.parseDelete()
  → RdbmsService.delete()
  → Removes TableRow entities
```

---

### 4. "Indexing" → TableIndex Entity

**How it works**:
1. When creating table, indexes are specified
2. `TableIndex` entities are created
3. Stored in database via `TableIndexRepository`
4. Can be queried later (metadata)

**Example**:
```sql
CREATE TABLE products (
    id INTEGER,
    category_id INTEGER,
    INDEX idx_category (category_id)
);
```

**Implementation**:
- `TableIndex.java` - Entity storing index metadata
- Index creation in `RdbmsService.createTable()`

---

### 5. "Primary and Unique Keys" → TableKey Entity + Validation

**Primary Key Flow**:
1. Table created with PRIMARY KEY constraint
2. `TableKey` entity created with `KeyType.PRIMARY`
3. On INSERT/UPDATE: `validatePrimaryKey()` checks:
   - Value is not NULL
   - Value is unique (no duplicates)

**Unique Key Flow**:
1. Table created with UNIQUE constraint
2. `TableKey` entity created with `KeyType.UNIQUE`
3. On INSERT/UPDATE: `validateUniqueKeys()` checks:
   - If value exists, it must be unique

**Code Location**:
- `TableKey.java` - Entity
- `RdbmsService.validatePrimaryKey()` - Validation logic
- `RdbmsService.validateUniqueKeys()` - Validation logic

---

### 6. "Joining" → RdbmsService.join()

**How JOIN Works**:
1. User executes: `SELECT * FROM products p JOIN categories c ON p.category_id = c.id`
2. `SqlParserService.parseJoin()` extracts:
   - Left table: products
   - Right table: categories
   - Join condition: p.category_id = c.id
3. `RdbmsService.join()`:
   - Fetches all rows from both tables
   - Iterates through left table rows
   - For each row, finds matching rows in right table
   - Combines matching rows into result set
4. Returns combined data

**Implementation**:
- `RdbmsService.join()` - Core JOIN logic
- Supports INNER, LEFT, RIGHT joins

---

### 7. "SQL Interface" → SqlParserService

**How SQL Parsing Works**:
1. User sends SQL string: `"SELECT * FROM products"`
2. `SqlParserService.executeSql()`:
   - Detects statement type (SELECT, INSERT, etc.)
   - Calls appropriate parser method
   - Converts SQL to DTO (Data Transfer Object)
   - Calls `RdbmsService` method
   - Returns result

**Parser Methods**:
- `parseCreateTable()` - Parses CREATE TABLE
- `parseInsert()` - Parses INSERT INTO
- `parseSelect()` - Parses SELECT
- `parseUpdate()` - Parses UPDATE
- `parseDelete()` - Parses DELETE
- `parseJoin()` - Parses JOIN queries

**Technology**: Regex-based parsing (simple but effective)

---

### 8. "Interactive REPL Mode" → Controller + Frontend

**Backend REPL**:
- Endpoint: `POST /api/rdbms/sql`
- Accepts: `{ "sql": "SELECT * FROM products" }`
- Returns: Query results or error

**Frontend REPL**:
- Textarea for SQL input
- Execute button
- Results display area
- Error display area

**REPL Flow**:
```
User types SQL → Clicks Execute → 
Frontend sends to /api/rdbms/sql → 
Backend parses and executes → 
Returns results → 
Frontend displays results
```

**Files**:
- `RdbmsController.executeSql()` - Backend endpoint
- `App.js` SQL REPL tab - Frontend interface

---

### 9. "Trivial Web App with CRUD" → React Frontend

**Web App Components**:

1. **SQL REPL Tab**:
   - User can type any SQL
   - Execute CREATE, INSERT, SELECT, UPDATE, DELETE
   - See results immediately

2. **Table Browser Tab**:
   - Lists all tables
   - Click table to view data
   - Shows all rows in table format

**CRUD Demonstration**:
- **Create**: `CREATE TABLE ...` in SQL REPL
- **Read**: `SELECT * FROM ...` or use table browser
- **Update**: `UPDATE ... SET ... WHERE ...`
- **Delete**: `DELETE FROM ... WHERE ...`

**Files**:
- `frontend/src/App.js` - Main React component
- `frontend/src/App.css` - Styling

---

### 10. "Spring Boot + Gradle" → Build Configuration

**Gradle Setup**:
- `backend/build.gradle` - Dependency management
- `backend/settings.gradle` - Project settings
- `backend/gradlew` - Gradle wrapper (executable)

**Spring Boot Layers**:
```
┌─────────────┐
│ Controller  │  ← REST endpoints
└──────┬──────┘
       │
┌──────▼──────┐
│  Service    │  ← Business logic
└──────┬──────┘
       │
┌──────▼──────┐
│ Repository  │  ← Data access
└──────┬──────┘
       │
┌──────▼──────┐
│   Entity    │  ← Data model
└─────────────┘
```

---

### 11. "Entities, Services, Repository" → Package Structure

**Entities** (`entity/` package):
- `DatabaseTable.java` - Table metadata
- `TableColumn.java` - Column definitions
- `TableIndex.java` - Index definitions
- `TableKey.java` - Key constraints
- `TableRow.java` - Row data

**Repositories** (`repository/` package):
- Extend `JpaRepository<Entity, Long>`
- Spring Data JPA provides CRUD automatically
- Custom query methods as needed

**Services** (`service/` package):
- `RdbmsService.java` - Core RDBMS operations
- `SqlParserService.java` - SQL parsing

**All follow Spring Boot best practices**

---

### 12. "Separate React Web App" → Frontend Directory

**Separation**:
- Completely independent codebase
- Different build system (npm vs Gradle)
- Different port (3000 vs 8080)
- Communication via REST API

**React Structure**:
```
frontend/
├── src/
│   ├── App.js       ← Main component
│   ├── App.css      ← Styles
│   └── index.js     ← Entry point
└── package.json     ← Dependencies
```

**Communication**:
- Frontend makes HTTP requests to backend
- CORS configured in `application.properties`
- API base URL: `http://localhost:8080/api/rdbms`

---

### 13. "E-Commerce Business Requirement" → DataInitializer

**What it does**:
- Runs on application startup
- Creates e-commerce tables:
  - `products` - Product catalog
  - `categories` - Product categories
  - `orders` - Customer orders
  - `order_items` - Order line items
- Inserts sample data
- Demonstrates relationships

**File**: `backend/src/main/java/com/pesapal/rdbms/config/DataInitializer.java`

**Demonstrates**:
- Table creation
- Relationships (products → categories)
- JOIN operations
- Real-world use case

---

## Data Flow Example: Creating and Querying a Table

### Step 1: User Creates Table
```
User types in SQL REPL:
  CREATE TABLE products (
      id INTEGER,
      name VARCHAR(100),
      price DECIMAL(10,2),
      PRIMARY KEY (id)
  );

Frontend sends:
  POST /api/rdbms/sql
  { "sql": "CREATE TABLE products ..." }

Backend:
  1. RdbmsController.executeSql()
  2. SqlParserService.executeSql()
  3. SqlParserService.parseCreateTable()
  4. Creates CreateTableRequest DTO
  5. RdbmsService.createTable()
  6. Creates DatabaseTable entity
  7. Creates TableColumn entities (id, name, price)
  8. Creates TableKey entity (PRIMARY KEY on id)
  9. Saves via repositories
  10. Returns success

Frontend displays: Success message
```

### Step 2: User Inserts Data
```
User types:
  INSERT INTO products (id, name, price) VALUES (1, 'Laptop', 999.99);

Backend:
  1. SqlParserService.parseInsert()
  2. Creates InsertRequest DTO
  3. RdbmsService.insert()
  4. Validates primary key (id=1 is unique)
  5. Creates TableRow entity
  6. Stores data as JSON: {"id":1,"name":"Laptop","price":999.99}
  7. Saves via TableRowRepository

Frontend displays: Success
```

### Step 3: User Queries Data
```
User types:
  SELECT * FROM products;

Backend:
  1. SqlParserService.parseSelect()
  2. Creates SelectRequest DTO
  3. RdbmsService.select()
  4. Retrieves TableRow entities
  5. Parses JSON data from each row
  6. Returns List<Map<String, Object>>

Frontend displays:
  [
    {
      "id": 1,
      "name": "Laptop",
      "price": 999.99
    }
  ]
```

---

## Key Design Decisions

### Why JSON for Row Data?
- **Flexibility**: Can store any column combination
- **Simplicity**: No need for dynamic table creation in H2
- **Trade-off**: Less efficient than native columns, but simpler to implement

### Why Separate Frontend?
- **Separation of Concerns**: UI separate from business logic
- **Scalability**: Can replace frontend without changing backend
- **Technology Choice**: React is modern and popular

### Why H2 In-Memory?
- **Simplicity**: No external database setup needed
- **Portability**: Works anywhere Java runs
- **Trade-off**: Data lost on restart (acceptable for demo)

### Why Regex SQL Parser?
- **Simplicity**: No external parser dependency
- **Sufficient**: Handles required SQL statements
- **Trade-off**: Less robust than full SQL parser (but meets requirements)

---

This architecture demonstrates understanding of:
- Spring Boot best practices
- RESTful API design
- Frontend-backend separation
- Database concepts
- Software architecture patterns
