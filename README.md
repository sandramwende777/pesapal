# Simple RDBMS - Interview Challenge

This project implements a simple Relational Database Management System (RDBMS) with a Spring Boot backend and React frontend. The system supports table creation, CRUD operations, indexing, primary/unique keys, and JOIN operations.

## Project Structure

```
pesapal/
├── backend/          # Spring Boot application (Gradle)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/pesapal/rdbms/
│   │   │   │   ├── entity/      # JPA entities for RDBMS metadata
│   │   │   │   ├── repository/  # JPA repositories
│   │   │   │   ├── service/     # Business logic and SQL parser
│   │   │   │   ├── controller/  # REST API endpoints
│   │   │   │   ├── dto/         # Data Transfer Objects
│   │   │   │   └── config/      # Configuration and initialization
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   └── test/
│   ├── build.gradle
│   └── settings.gradle
└── frontend/         # React application
    ├── src/
    │   ├── App.js
    │   ├── App.css
    │   ├── index.js
    │   └── index.css
    ├── public/
    └── package.json
```

## Features

### Backend (Spring Boot)
- **Entities**: JPA entities for tables, columns, indexes, keys, and rows
- **Repositories**: Data access layer using Spring Data JPA
- **Services**: 
  - `RdbmsService`: Core RDBMS operations (CREATE TABLE, INSERT, SELECT, UPDATE, DELETE, JOIN)
  - `SqlParserService`: SQL parser for REPL mode
- **REST API**: RESTful endpoints for all operations
- **SQL REPL**: Interactive SQL interface via `/api/rdbms/sql` endpoint

### Frontend (React)
- **SQL REPL Interface**: Interactive SQL query interface
- **Table Browser**: View tables and their data
- **CRUD Operations**: Create, read, update, and delete data through the UI

### Supported Operations
- ✅ CREATE TABLE with column definitions
- ✅ DROP TABLE to remove tables
- ✅ Data types: VARCHAR, INTEGER, BIGINT, DECIMAL, BOOLEAN, DATE, TIMESTAMP, TEXT
- ✅ PRIMARY KEY constraints
- ✅ UNIQUE constraints
- ✅ Indexes (with unique option)
- ✅ INSERT with data validation
- ✅ SELECT with WHERE, LIMIT, OFFSET
- ✅ UPDATE with WHERE clause
- ✅ DELETE with WHERE clause
- ✅ JOIN operations (INNER, LEFT, RIGHT)
- ✅ WHERE clause operators: =, !=, <>, >, <, >=, <=, LIKE, IS NULL, IS NOT NULL
- ✅ **Interactive REPL mode** (command-line SQL interface)

## Prerequisites

- Java 17 or higher
- Node.js 16+ and npm
- Gradle 8.5+ (or use the Gradle wrapper)

## Getting Started

### Backend Setup

1. Navigate to the backend directory:
```bash
cd backend
```

2. Build the project:
```bash
./gradlew build
```

3. Run the application:
```bash
./gradlew bootRun
```

The backend will start on `http://localhost:8080`

### Running in REPL Mode (Interactive Command-Line)

To use the interactive SQL REPL (Read-Eval-Print-Loop):

```bash
cd backend
./gradlew bootRun --args='--repl.enabled=true'
```

Or if using a JAR file:
```bash
java -jar build/libs/rdbms-0.0.1-SNAPSHOT.jar --repl.enabled=true
```

This starts an interactive command-line interface:
```
╔═══════════════════════════════════════════════════════════════╗
║           Simple RDBMS - Interactive SQL Interface            ║
║                        Version 1.0                            ║
╚═══════════════════════════════════════════════════════════════╝

rdbms> SHOW TABLES;
+------------+---------+------+
| Table Name | Columns | Rows |
+------------+---------+------+
| products   |       6 |    4 |
| categories |       3 |    3 |
+------------+---------+------+
2 table(s) in database
Time: 5 ms

rdbms> SELECT * FROM products WHERE price > 100;
...
```

Type `help` for examples, `quit` to exit.

### Frontend Setup

1. Navigate to the frontend directory:
```bash
cd frontend
```

2. Install dependencies:
```bash
npm install
```

3. Start the development server:
```bash
npm start
```

The frontend will start on `http://localhost:3000`

## API Endpoints

### REST API

- `POST /api/rdbms/tables` - Create a new table
- `GET /api/rdbms/tables` - List all tables
- `GET /api/rdbms/tables/{tableName}` - Get table metadata
- `DELETE /api/rdbms/tables/{tableName}` - Drop a table
- `POST /api/rdbms/insert` - Insert a row
- `POST /api/rdbms/select` - Select rows
- `PUT /api/rdbms/update` - Update rows
- `DELETE /api/rdbms/delete` - Delete rows
- `POST /api/rdbms/join` - Perform a JOIN operation
- `POST /api/rdbms/sql` - Execute SQL query (REPL mode)

### SQL Examples

```sql
-- Create a table
CREATE TABLE products (
    id INTEGER,
    name VARCHAR(100),
    price DECIMAL(10,2),
    stock INTEGER,
    PRIMARY KEY (id)
);

-- Insert data
INSERT INTO products (id, name, price, stock) VALUES (1, 'Laptop', 999.99, 50);

-- Select data
SELECT * FROM products;
SELECT name, price FROM products LIMIT 10;

-- WHERE clause with comparison operators
SELECT * FROM products WHERE price > 500;
SELECT * FROM products WHERE price >= 100 AND price <= 1000;
SELECT * FROM products WHERE stock != 0;
SELECT * FROM products WHERE name LIKE '%Laptop%';
SELECT * FROM products WHERE description IS NOT NULL;

-- Update data
UPDATE products SET price = 899.99 WHERE id = 1;

-- Delete data
DELETE FROM products WHERE id = 1;

-- Drop a table
DROP TABLE products;

-- Join tables
SELECT * FROM products INNER JOIN categories ON products.category_id = categories.id;
SELECT * FROM orders LEFT JOIN order_items ON orders.id = order_items.order_id;

-- Show tables
SHOW TABLES;

-- Describe table
DESCRIBE products;
```

## Sample E-Commerce Data

The application automatically initializes sample e-commerce tables **using our RDBMS** on startup:
- **products**: Product catalog with id, name, description, price, stock, category_id
- **categories**: Product categories with id, name, description
- **orders**: Customer orders with id, customer_id, order_date, total_amount, status
- **order_items**: Order line items with id, order_id, product_id, quantity, price

**Important**: These tables are created **via our RDBMS** (using CREATE TABLE in DataInitializer), not as traditional JPA entities. This demonstrates the RDBMS in action with a real-world e-commerce use case.

## Architecture Notes

### Data Storage
- The RDBMS metadata (tables, columns, indexes, keys) is stored in JPA entities
- Row data is stored as JSON strings in the `TableRow` entity
- The actual database (H2 in-memory) stores the RDBMS metadata

### Constraints
- Primary key validation ensures uniqueness
- Unique key validation prevents duplicates
- Nullable constraints are enforced
- Foreign key relationships are supported through JOIN operations

### Limitations
- JOIN operations are limited to equality joins
- No support for complex SQL features (subqueries, aggregations like COUNT/SUM, etc.)
- OR conditions in WHERE clauses are not supported (only AND)
- Indexes are stored as metadata but don't optimize query performance
- Data types are validated but not strictly enforced at the storage level

## Testing

### Backend Tests
```bash
cd backend
./gradlew test
```

The test suite includes:
- CREATE TABLE tests (with PRIMARY KEY, UNIQUE constraints)
- INSERT tests (including constraint violation tests)
- SELECT tests (basic, WHERE, LIMIT, column projection)
- UPDATE and DELETE tests
- DROP TABLE tests
- JOIN tests (INNER, LEFT)
- SQL Parser tests (all SQL commands)

### Manual Testing
1. Use the React UI to execute SQL queries
2. Use the REST API endpoints directly (Postman, curl, etc.)
3. Access H2 console at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:rdbmsdb`)

## Technologies Used

### Backend
- Spring Boot 3.2.0
- Spring Data JPA
- H2 Database (in-memory)
- Lombok
- Gradle

### Frontend
- React 18.2.0
- Axios
- CSS3

## Credits

This project was built as an interview challenge. All code was written specifically for this challenge, with standard Spring Boot and React patterns and best practices applied.

## License

This project is for interview/assessment purposes only.
