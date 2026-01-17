# Metadata Approach: Architecture Summary

## ✅ Decision: Using Metadata Entities

We're using **metadata entities** to store information about user-created tables. This is the current approach and has been updated with clear documentation.

## Why Metadata Entities?

### The Challenge
We're **building a simple RDBMS**, not just using one. When users create tables dynamically:
```sql
CREATE TABLE products (id INTEGER, name VARCHAR(100), PRIMARY KEY (id));
```

We need to **remember**:
- ✅ Table exists
- ✅ Column definitions (id INTEGER, name VARCHAR(100))
- ✅ Constraints (PRIMARY KEY on id)
- ✅ Indexes

### The Solution: Metadata Entities

We store this information in **metadata entities**:

```
┌─────────────────────────────────────┐
│  DatabaseTable (Metadata)          │
│  - Stores table name                │
│  - Links to columns, keys, indexes  │
└─────────────────────────────────────┘
         │
         ├── TableColumn (Metadata)
         │   - Column name, type, nullable
         │
         ├── TableKey (Metadata)
         │   - Primary/unique key constraints
         │
         ├── TableIndex (Metadata)
         │   - Index definitions
         │
         └── TableRow (Data)
             - Actual row data as JSON
```

## Architecture Flow

### 1. Creating a Table
```
User: CREATE TABLE products (id INTEGER, name VARCHAR(100))

Service:
  1. Create DatabaseTable entity (metadata)
  2. Create TableColumn entities (metadata)
  3. Create TableKey entities (metadata)
  4. Store in database

Result: Metadata stored, ready to accept data
```

### 2. Inserting Data
```
User: INSERT INTO products (id, name) VALUES (1, 'Laptop')

Service:
  1. Retrieve DatabaseTable metadata
  2. Validate columns exist (using TableColumn metadata)
  3. Validate constraints (using TableKey metadata)
  4. Create TableRow entity with JSON data
  5. Store in database

Result: Data stored, validated against metadata
```

### 3. Querying Data
```
User: SELECT * FROM products

Service:
  1. Retrieve DatabaseTable metadata
  2. Get all TableRow entities for this table
  3. Parse JSON data
  4. Filter using WHERE clause
  5. Return results

Result: Data retrieved using metadata
```

## Benefits of Metadata Approach

### ✅ Persistence
- Schema survives application restarts
- Can query metadata later
- Data doesn't disappear

### ✅ Queryable
- Can execute "SHOW TABLES" (query DatabaseTable entities)
- Can execute "DESCRIBE table" (query TableColumn entities)
- Can query constraints (query TableKey entities)

### ✅ Validation
- Validate columns exist (using TableColumn metadata)
- Validate primary keys (using TableKey metadata)
- Validate unique keys (using TableKey metadata)
- Validate nullable constraints (using TableColumn metadata)

### ✅ Demonstrates Understanding
- Shows how databases store their own metadata
- Demonstrates knowledge of database internals
- More complete/realistic implementation

## Code Structure

### Metadata Entities
```
entity/
├── DatabaseTable.java    → Stores table metadata
├── TableColumn.java     → Stores column definitions
├── TableKey.java        → Stores key constraints
├── TableIndex.java      → Stores index definitions
└── TableRow.java        → Stores actual row data (JSON)
```

### Service Layer
```
service/
└── RdbmsService.java    → Uses metadata to:
                          - Create tables (store metadata)
                          - Insert data (validate using metadata)
                          - Query data (retrieve using metadata)
                          - Update/Delete (validate using metadata)
```

## Key Points for Interview

### When Asked: "Why metadata entities?"

**Answer:**
> "We're building a simple RDBMS, so we need to store schema information somewhere. 
> We use metadata entities because:
> 
> 1. **Persistence**: Schema survives restarts
> 2. **Queryable**: Can query metadata (SHOW TABLES, DESCRIBE)
> 3. **Validation**: Use metadata to validate constraints
> 4. **Realistic**: Similar to how real databases store their metadata
> 
> The metadata entities (DatabaseTable, TableColumn, etc.) are the ENGINE.
> The actual user data is stored in TableRow entities as JSON to support dynamic schemas."

### When Asked: "Why not in-memory?"

**Answer:**
> "We could use in-memory Maps/Lists, but metadata entities provide:
> 
> - **Persistence**: Data survives restarts
> - **Queryability**: Can query metadata via SQL
> - **Completeness**: More realistic database implementation
> 
> For a production system, persistence is important. For a demo, in-memory 
> would be simpler but less complete."

## Updated Code

All entities and services now have:
- ✅ Clear JavaDoc comments explaining the metadata approach
- ✅ Comments explaining why we use metadata entities
- ✅ Documentation of the architecture decision

## Files Updated

1. **DatabaseTable.java** - Added architecture documentation
2. **TableColumn.java** - Added metadata explanation
3. **TableKey.java** - Added constraint metadata explanation
4. **TableIndex.java** - Added index metadata explanation
5. **TableRow.java** - Added JSON storage explanation
6. **RdbmsService.java** - Added service-level architecture documentation

All code now clearly explains the metadata approach! 🎉
