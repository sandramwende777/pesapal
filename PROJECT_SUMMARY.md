# Project Summary

## Overview

This project implements a simple Relational Database Management System (RDBMS) as an interview challenge. The system consists of:

1. **Backend**: Spring Boot application (Java 17, Gradle) that implements the RDBMS engine
2. **Frontend**: React web application for interacting with the RDBMS

## Architecture

### Backend Architecture

The backend uses a layered architecture:

- **Entities** (`entity/`): JPA entities that store RDBMS metadata
  - `DatabaseTable`: Represents a table
  - `TableColumn`: Represents a column with data type
  - `TableIndex`: Represents an index
  - `TableKey`: Represents primary/unique keys
  - `TableRow`: Stores actual row data as JSON

- **Repositories** (`repository/`): Spring Data JPA repositories for data access

- **Services** (`service/`):
  - `RdbmsService`: Core RDBMS operations (CREATE TABLE, INSERT, SELECT, UPDATE, DELETE, JOIN)
  - `SqlParserService`: Parses SQL statements and converts them to service calls

- **Controllers** (`controller/`): REST API endpoints

- **DTOs** (`dto/`): Data Transfer Objects for API requests/responses

### Frontend Architecture

- **React Components**: Single-page application with:
  - SQL REPL interface for executing queries
  - Table browser for viewing table data
  - Tab-based navigation

## Key Features Implemented

### ✅ Core Requirements

1. **Table Declaration**
   - Support for multiple column data types (VARCHAR, INTEGER, BIGINT, DECIMAL, BOOLEAN, DATE, TIMESTAMP, TEXT)
   - Column definitions with nullable constraints
   - Default values

2. **CRUD Operations**
   - CREATE TABLE
   - INSERT
   - SELECT (with WHERE, LIMIT, OFFSET)
   - UPDATE (with WHERE)
   - DELETE (with WHERE)

3. **Indexing**
   - Basic index support
   - Unique index option

4. **Primary and Unique Keys**
   - Primary key constraints with uniqueness validation
   - Unique key constraints with uniqueness validation

5. **Joining**
   - INNER JOIN
   - LEFT JOIN
   - RIGHT JOIN (basic support)

6. **SQL Interface**
   - SQL parser for common SQL statements
   - Interactive REPL mode via REST API

7. **Web Application**
   - React-based UI
   - SQL query interface
   - Table data browser
   - Demonstrates CRUD operations

### E-Commerce Demo

The system includes pre-initialized e-commerce tables:
- `products`: Product catalog
- `categories`: Product categories
- `orders`: Customer orders
- `order_items`: Order line items

These tables demonstrate relationships and JOIN operations.

## Technical Decisions

### Data Storage
- Row data stored as JSON strings in `TableRow` entity
- Metadata (tables, columns, indexes, keys) stored as separate JPA entities
- Uses H2 in-memory database for metadata storage

### SQL Parser
- Simple regex-based parser
- Supports basic SQL syntax
- Extensible for additional features

### Constraints
- Primary key uniqueness enforced at application level
- Unique key constraints enforced
- Nullable constraints validated
- Type validation performed

## Limitations

As a "simple" RDBMS, the following are intentionally limited:

1. WHERE clauses support only equality conditions (no >, <, LIKE, etc.)
2. JOIN operations limited to equality joins
3. No support for:
   - Subqueries
   - Aggregations (COUNT, SUM, etc.)
   - Transactions
   - Foreign key referential integrity
   - Complex SQL features

These limitations are acceptable for a demonstration system and can be extended as needed.

## File Structure

```
pesapal/
├── backend/
│   ├── src/main/java/com/pesapal/rdbms/
│   │   ├── entity/          # JPA entities
│   │   ├── repository/      # Data repositories
│   │   ├── service/         # Business logic
│   │   ├── controller/      # REST controllers
│   │   ├── dto/            # Data transfer objects
│   │   └── config/         # Configuration
│   ├── build.gradle
│   └── gradlew
├── frontend/
│   ├── src/
│   │   ├── App.js          # Main React component
│   │   └── App.css         # Styles
│   └── package.json
├── README.md
├── QUICKSTART.md
└── PROJECT_SUMMARY.md
```

## Testing the System

1. Start backend: `cd backend && ./gradlew bootRun`
2. Start frontend: `cd frontend && npm install && npm start`
3. Access UI at `http://localhost:3000`
4. Try SQL queries in the REPL interface
5. Browse tables and their data

## Credits

This project was built from scratch for the interview challenge. Standard Spring Boot and React patterns were used, following best practices for:
- Clean architecture
- Separation of concerns
- RESTful API design
- Modern React patterns
