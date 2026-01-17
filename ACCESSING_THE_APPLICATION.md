# How to Access the Application

## The 404 Error Explained

When you visit `http://localhost:8080` in your browser, you see a 404 error because:

1. **The backend is a REST API**, not a web page
2. There's no HTML page at the root path `/`
3. All API endpoints are under `/api/rdbms/*`

## Solution: Use the Frontend

The React frontend provides a web interface to interact with the RDBMS.

### Start the Frontend

```bash
cd frontend
npm start
```

The frontend will:
- Open automatically at `http://localhost:3000`
- Connect to the backend API at `http://localhost:8080`
- Provide a SQL REPL interface
- Show a table browser

## Alternative: Test the API Directly

### Using curl:

```bash
# List all tables
curl http://localhost:8080/api/rdbms/tables

# Execute SQL
curl -X POST http://localhost:8080/api/rdbms/sql \
  -H "Content-Type: application/json" \
  -d '{"sql": "SHOW TABLES"}'

# Select data
curl -X POST http://localhost:8080/api/rdbms/sql \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT * FROM products"}'
```

### Using Browser:

After restarting the backend, visit:
- `http://localhost:8080/` - Shows API information
- `http://localhost:8080/api/rdbms/tables` - Lists all tables (JSON)
- `http://localhost:8080/h2-console` - H2 database console

## Restart Backend to See Root Endpoint

I've added a root endpoint that shows API information. To see it:

1. Stop the current backend (Ctrl+C in the terminal running `./gradlew bootRun`)
2. Rebuild and restart:
   ```bash
   cd backend
   ./gradlew clean build
   ./gradlew bootRun
   ```
3. Visit `http://localhost:8080/` - You'll see API information instead of 404

## Quick Start

1. **Backend** (already running): `http://localhost:8080`
2. **Frontend** (start it): 
   ```bash
   cd frontend
   npm start
   ```
3. **Access**: Open `http://localhost:3000` in your browser

The frontend provides a complete web interface for:
- Running SQL queries
- Browsing tables
- Viewing table data
- Creating tables
- Inserting/updating/deleting data
