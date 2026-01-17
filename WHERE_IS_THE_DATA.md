# Where is the Data Stored? (Simple Explanation)

## Quick Answer

**The data is stored in the H2 database, which is currently configured to be IN MEMORY (RAM), not in a file.**

This means:
- ✅ Data exists while the app is running
- ❌ Data is **lost** when you stop the app
- ❌ There is **no file** on disk storing the data

---

## The Configuration File

The data storage location is configured in:

**File:** `backend/src/main/resources/application.properties`

**Line 5:**
```properties
spring.datasource.url=jdbc:h2:mem:rdbmsdb
```

The important part is `:mem:` which means **"in memory"** (RAM).

---

## Current Setup: In-Memory Database

### What This Means

```
┌─────────────────────────────────────┐
│  Your Computer's RAM (Memory)       │
│                                      │
│  ┌──────────────────────────────┐  │
│  │  H2 Database (in memory)      │  │
│  │                              │  │
│  │  database_tables             │  │
│  │  table_columns               │  │
│  │  table_keys                  │  │
│  │  table_indexes               │  │
│  │  table_rows                  │  │
│  │                              │  │
│  │  All your data is here!      │  │
│  └──────────────────────────────┘  │
└─────────────────────────────────────┘
         ↑
         │
    When app stops,
    this disappears!
```

### Pros and Cons

**Pros:**
- ✅ Fast (RAM is very fast)
- ✅ No files to manage
- ✅ Good for testing/demos
- ✅ Data is fresh every time you start

**Cons:**
- ❌ Data is lost when app stops
- ❌ Can't share data between app restarts
- ❌ Not good for production

---

## How to Change to File-Based Storage

If you want the data to persist in a file, change this line:

### Current (In Memory):
```properties
spring.datasource.url=jdbc:h2:mem:rdbmsdb
```

### Change to (File-Based):
```properties
spring.datasource.url=jdbc:h2:file:./data/rdbmsdb
```

**What this does:**
- Creates a file called `rdbmsdb.mv.db` in `backend/data/` folder
- Data persists even after app stops
- Data is saved to disk

### Complete File-Based Configuration

```properties
# H2 Database Configuration (File-Based)
spring.datasource.url=jdbc:h2:file:./data/rdbmsdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA Configuration
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

**After this change:**
- Data file: `backend/data/rdbmsdb.mv.db`
- Data persists between app restarts
- You can see the file on disk

---

## Understanding the Data Layers

### Layer 1: Entity Files (Define Structure, NOT Data)

**Location:** `backend/src/main/java/com/pesapal/rdbms/entity/`

**Files:**
- `DatabaseTable.java` - Defines what a table looks like
- `TableColumn.java` - Defines what a column looks like
- `TableRow.java` - Defines what a row looks like

**What they do:**
- ❌ They DON'T store data
- ✅ They DEFINE the structure (like a blueprint)
- ✅ Spring Boot uses them to create database tables

**Analogy:** Like a recipe card - it describes the dish, but doesn't contain the actual food.

### Layer 2: Database (Actually Stores Data)

**Location:** H2 Database (currently in RAM)

**What it stores:**
- Actual table records
- Actual column definitions
- Actual row data
- Everything you create

**How to see it:**
1. Start the app
2. Go to: `http://localhost:8080/h2-console`
3. JDBC URL: `jdbc:h2:mem:rdbmsdb`
4. Username: `sa`
5. Password: (empty)
6. Click "Connect"
7. You'll see all your tables and data!

---

## Where Different Types of Data Are Stored

### 1. Metadata (Table Definitions)

**Stored in:** H2 Database tables
- `database_tables` table
- `table_columns` table
- `table_keys` table
- `table_indexes` table

**Example:**
```sql
-- When you create a table, this data goes into database_tables:
INSERT INTO database_tables (id, table_name) VALUES (1, 'products');
```

### 2. User Table Data (Row Data)

**Stored in:** H2 Database `table_rows` table

**Format:** JSON string in `row_data` column

**Example:**
```sql
-- When you insert data, it's stored like this:
INSERT INTO table_rows (id, table_id, row_data) 
VALUES (1, 1, '{"id":1,"name":"Laptop","price":999.99}');
```

---

## Visual: Where Data Lives

### Current Setup (In Memory)

```
┌─────────────────────────────────────────┐
│  application.properties                 │
│  spring.datasource.url=                │
│    jdbc:h2:mem:rdbmsdb  ← "in memory" │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│  H2 Database (In RAM)                   │
│  ┌───────────────────────────────────┐ │
│  │  database_tables                  │ │
│  │  | id | table_name              | │ │
│  │  | 1  | products               | │ │
│  │  | 2  | categories              | │ │
│  └───────────────────────────────────┘ │
│  ┌───────────────────────────────────┐ │
│  │  table_rows                       │ │
│  │  | id | table_id | row_data     | │ │
│  │  | 1  | 1        | {"id":1,...}  | │ │
│  └───────────────────────────────────┘ │
└─────────────────────────────────────────┘
         ↑
    Stored in RAM
    (Disappears when app stops)
```

### If Changed to File-Based

```
┌─────────────────────────────────────────┐
│  application.properties                 │
│  spring.datasource.url=                 │
│    jdbc:h2:file:./data/rdbmsdb          │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│  backend/data/rdbmsdb.mv.db (FILE)      │
│  ┌───────────────────────────────────┐ │
│  │  database_tables                  │ │
│  │  table_columns                    │ │
│  │  table_rows                       │ │
│  │  ... all your data ...            │ │
│  └───────────────────────────────────┘ │
└─────────────────────────────────────────┘
         ↑
    Physical file on disk
    (Persists after app stops)
```

---

## How to View Your Data

### Method 1: H2 Console (Web Interface)

1. **Start your app:**
   ```bash
   cd backend
   ./gradlew bootRun
   ```

2. **Open browser:**
   ```
   http://localhost:8080/h2-console
   ```

3. **Login:**
   - JDBC URL: `jdbc:h2:mem:rdbmsdb`
   - Username: `sa`
   - Password: (leave empty)

4. **See your data:**
   ```sql
   SELECT * FROM database_tables;
   SELECT * FROM table_rows;
   ```

### Method 2: Through the React UI

1. Start frontend: `npm start`
2. Go to `http://localhost:3000`
3. Click on tables in sidebar
4. View data in the UI

### Method 3: Through REST API

```bash
# Get all tables
curl http://localhost:8080/api/rdbms/tables

# Execute SQL
curl -X POST http://localhost:8080/api/rdbms/sql \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT * FROM database_tables"}'
```

---

## Summary Table

| What | Where | File? |
|------|-------|-------|
| **Entity definitions** | `backend/src/main/java/.../entity/*.java` | ✅ Yes (code files) |
| **Data storage config** | `backend/src/main/resources/application.properties` | ✅ Yes (config file) |
| **Actual data** | H2 Database (currently in RAM) | ❌ No (in memory) |
| **If file-based** | `backend/data/rdbmsdb.mv.db` | ✅ Yes (database file) |

---

## Key Points to Remember

1. **Entity files** (`*.java`) = Blueprints (define structure, not data)
2. **application.properties** = Configuration (tells where to store data)
3. **H2 Database** = Actual storage (currently in RAM)
4. **`:mem:`** = In memory (data lost on restart)
5. **`:file:`** = On disk (data persists)

---

## Quick Reference

### Current Setup
- **Storage:** RAM (memory)
- **Persistence:** ❌ No (lost on restart)
- **File:** None
- **Config:** `jdbc:h2:mem:rdbmsdb`

### If You Want Persistence
- **Storage:** Disk (file)
- **Persistence:** ✅ Yes (survives restart)
- **File:** `backend/data/rdbmsdb.mv.db`
- **Config:** `jdbc:h2:file:./data/rdbmsdb`

---

## For the Interview

**Question:** "Where is the data stored?"

**Answer:**
- "The data is stored in an H2 in-memory database, configured in `application.properties`"
- "Currently using `jdbc:h2:mem:rdbmsdb` which stores data in RAM"
- "This is good for demos but data is lost on restart"
- "For production, we'd change it to `jdbc:h2:file:./data/rdbmsdb` to persist to disk"
- "The entity classes define the structure, but the actual data is in the H2 database"
- "You can view it via H2 console at `/h2-console`"
