# How to Run the Application

## Prerequisites Check

✅ Java is installed: `/usr/bin/java`
✅ npm is installed: `11.6.2`

## Step-by-Step Instructions

### Step 1: Start the Backend (Spring Boot)

Open a terminal and run:

```bash
cd backend
./gradlew bootRun
```

**Wait for:**
```
Started RdbmsApplication in X.XXX seconds
```

The backend will be available at: `http://localhost:8080`

**What it does:**
- Starts Spring Boot application
- Creates H2 in-memory database
- Initializes e-commerce tables (products, categories, orders, order_items)
- Starts REST API server

### Step 2: Start the Frontend (React)

Open a **NEW terminal** (keep backend running) and run:

```bash
cd frontend
npm install    # Only needed first time
npm start
```

**What happens:**
- Installs dependencies (if needed)
- Starts React dev server
- Opens browser at `http://localhost:3000`
- Hot reload enabled (auto-refreshes on changes)

### Step 3: Use the Application

1. **Browser opens** at `http://localhost:3000`
2. **You'll see:**
   - Sidebar with tables (products, categories, orders, order_items)
   - Two tabs: "SQL REPL" and "Table Data"

3. **Try SQL REPL:**
   - Type: `SELECT * FROM products;`
   - Click "Execute SQL"
   - See results!

4. **Try Table Browser:**
   - Click "products" in sidebar
   - Click "Table Data" tab
   - See all product data in a table

## Quick Test Commands

### Test Backend API

```bash
# Get all tables
curl http://localhost:8080/api/rdbms/tables

# Execute SQL
curl -X POST http://localhost:8080/api/rdbms/sql \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT * FROM products"}'
```

### Test Frontend

Just open: `http://localhost:3000`

## Troubleshooting

### Backend won't start?
- Check if port 8080 is in use: `lsof -i :8080`
- Check Java version: `java -version` (needs Java 17+)
- Check logs for errors

### Frontend won't start?
- Install dependencies: `cd frontend && npm install`
- Check if port 3000 is in use: `lsof -i :3000`
- Check that backend is running first

### CORS errors?
- Make sure backend is running on port 8080
- Check `application.properties` has CORS enabled

### Can't see tables?
- Check backend logs for initialization errors
- Check H2 console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:rdbmsdb`
  - Username: `sa`
  - Password: (empty)

## What to Try

### 1. View Existing Tables
- Click "products" in sidebar
- See sample data

### 2. Execute SQL
```sql
-- View all products
SELECT * FROM products;

-- View products with price > 500
SELECT * FROM products WHERE price > 500;

-- Create a new table
CREATE TABLE customers (
    id INTEGER,
    name VARCHAR(100),
    email VARCHAR(100),
    PRIMARY KEY (id)
);

-- Insert data
INSERT INTO customers (id, name, email) VALUES (1, 'John Doe', 'john@example.com');

-- View the new table
SELECT * FROM customers;
```

### 3. Join Tables
```sql
SELECT * FROM products p 
INNER JOIN categories c 
ON p.category_id = c.id;
```

## Expected Behavior

### On Backend Start:
- ✅ Creates metadata tables (database_tables, table_columns, etc.)
- ✅ Creates e-commerce tables (products, categories, orders, order_items)
- ✅ Inserts sample data
- ✅ API available at `http://localhost:8080/api/rdbms/*`

### On Frontend Start:
- ✅ Opens browser at `http://localhost:3000`
- ✅ Shows sidebar with tables
- ✅ SQL REPL ready for queries
- ✅ Table browser ready to view data

## Stopping the Application

- **Backend**: Press `Ctrl+C` in backend terminal
- **Frontend**: Press `Ctrl+C` in frontend terminal

## Next Steps

Once running, you can:
1. Execute SQL queries
2. Browse table data
3. Create new tables
4. Insert/update/delete data
5. Test JOIN operations

Enjoy exploring the RDBMS! 🎉
