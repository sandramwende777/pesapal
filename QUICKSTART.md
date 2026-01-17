# Quick Start Guide

## Prerequisites Check

Before starting, ensure you have:
- Java 17+ installed (`java -version`)
- Node.js 16+ installed (`node -v`)
- npm installed (`npm -v`)

## Step-by-Step Setup

### 1. Start the Backend (Spring Boot)

Open a terminal and run:

```bash
cd backend
./gradlew bootRun
```

Wait for the message: `Started RdbmsApplication in X.XXX seconds`

The backend API will be available at: `http://localhost:8080`

### 2. Start the Frontend (React)

Open a **new terminal** and run:

```bash
cd frontend
npm install
npm start
```

The React app will automatically open in your browser at: `http://localhost:3000`

## Testing the Application

### Using the Web UI

1. The React app will show sample e-commerce tables (products, categories, orders, order_items)
2. Click on a table in the sidebar to view its data
3. Use the "SQL REPL" tab to execute SQL queries

### Sample SQL Queries to Try

```sql
-- View all products
SELECT * FROM products;

-- View products with price > 500
SELECT * FROM products WHERE price > 500;

-- Join products with categories
SELECT * FROM products p INNER JOIN categories c ON p.category_id = c.id;

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

### Using the REST API

You can also test using curl or Postman:

```bash
# List all tables
curl http://localhost:8080/api/rdbms/tables

# Execute SQL
curl -X POST http://localhost:8080/api/rdbms/sql \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT * FROM products"}'
```

## Troubleshooting

### Backend won't start
- Check if port 8080 is already in use
- Ensure Java 17+ is installed
- Try: `./gradlew clean build` then `./gradlew bootRun`

### Frontend won't start
- Check if port 3000 is already in use
- Delete `node_modules` and run `npm install` again
- Check that the backend is running first

### CORS errors
- Ensure the backend is running on port 8080
- Check `application.properties` has CORS enabled for `http://localhost:3000`

## Next Steps

- Read the full [README.md](README.md) for detailed documentation
- Explore the API endpoints
- Try creating your own tables and queries
- Experiment with JOIN operations
