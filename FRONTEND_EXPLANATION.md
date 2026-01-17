# Frontend (React) Explanation

## Overview

The frontend is a **React web application** that provides a user interface for interacting with our RDBMS. It's a separate application that communicates with the backend via REST API.

## Architecture

```
┌─────────────────────────────────────┐
│  React Frontend (Port 3000)         │
│  - User Interface                   │
│  - SQL REPL                         │
│  - Table Browser                    │
└──────────────┬──────────────────────┘
               │
               │ HTTP Requests (axios)
               │ POST /api/rdbms/sql
               │ GET /api/rdbms/tables
               │ POST /api/rdbms/select
               ▼
┌─────────────────────────────────────┐
│  Spring Boot Backend (Port 8080)   │
│  - RdbmsController                 │
│  - RdbmsService                    │
│  - Our RDBMS Engine                │
└─────────────────────────────────────┘
```

## File Structure

```
frontend/
├── public/
│   └── index.html          ← HTML entry point
├── src/
│   ├── index.js            ← React entry point
│   ├── index.css          ← Global styles
│   ├── App.js             ← Main component (THE BRAIN)
│   └── App.css            ← Component styles
└── package.json           ← Dependencies
```

---

## Key Files Explained

### 1. `index.html` - The HTML Shell

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <title>RDBMS Demo - E-Commerce</title>
  </head>
  <body>
    <div id="root"></div>  ← React will render here!
  </body>
</html>
```

**What it does:**
- Basic HTML structure
- Provides a `<div id="root">` where React renders everything
- That's it! React takes over from here.

---

### 2. `index.js` - React Entry Point

```javascript
import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

**What it does:**
1. Finds the `<div id="root">` from index.html
2. Renders the `<App />` component inside it
3. `<React.StrictMode>` helps catch bugs during development

**Flow:**
```
index.html → index.js → App.js
```

---

### 3. `App.js` - The Main Component (THE BRAIN)

This is where all the logic lives! Let's break it down:

#### State Management (React Hooks)

```javascript
const [activeTab, setActiveTab] = useState('sql');
const [tables, setTables] = useState([]);
const [selectedTable, setSelectedTable] = useState(null);
const [tableData, setTableData] = useState([]);
const [sqlQuery, setSqlQuery] = useState('');
const [sqlResult, setSqlResult] = useState(null);
const [sqlError, setSqlError] = useState(null);
const [loading, setLoading] = useState(false);
```

**What is State?**
- State = Data that can change
- When state changes, React re-renders the UI
- `useState` creates state variables

**Each State Variable:**
- `activeTab` - Which tab is active? ('sql' or 'data')
- `tables` - List of all tables from backend
- `selectedTable` - Which table is currently selected?
- `tableData` - Data rows for selected table
- `sqlQuery` - SQL text user types
- `sqlResult` - Results from SQL execution
- `sqlError` - Error message if SQL fails
- `loading` - Is something loading? (shows spinner)

#### API Base URL

```javascript
const API_BASE_URL = 'http://localhost:8080/api/rdbms';
```

**What it does:**
- Base URL for all API calls
- All requests go to: `http://localhost:8080/api/rdbms/...`

---

### 4. Key Functions

#### `loadTables()` - Get All Tables

```javascript
const loadTables = async () => {
  try {
    const response = await axios.get(`${API_BASE_URL}/tables`);
    setTables(response.data);  // Update state with tables
  } catch (error) {
    console.error('Error loading tables:', error);
  }
};
```

**What it does:**
1. Sends GET request to `/api/rdbms/tables`
2. Backend returns list of tables
3. Updates `tables` state
4. UI automatically re-renders showing the tables

**Flow:**
```
User clicks "Refresh" 
  → loadTables() called
  → GET /api/rdbms/tables
  → Backend returns tables
  → setTables(response.data)
  → UI updates showing tables
```

#### `executeSql()` - Execute SQL Query

```javascript
const executeSql = async () => {
  try {
    setLoading(true);  // Show loading spinner
    setSqlError(null);  // Clear previous errors
    
    const response = await axios.post(`${API_BASE_URL}/sql`, {
      sql: sqlQuery  // Send SQL text to backend
    });
    
    setSqlResult(response.data);  // Show results
    
    // If CREATE TABLE, refresh table list
    if (sqlQuery.toUpperCase().trim().startsWith('CREATE TABLE')) {
      loadTables();
    }
  } catch (error) {
    setSqlError(error.response?.data?.error || error.message);
    setSqlResult(null);
  } finally {
    setLoading(false);  // Hide loading spinner
  }
};
```

**What it does:**
1. User types SQL in textarea
2. User clicks "Execute SQL"
3. Sends POST request with SQL text
4. Backend parses and executes SQL
5. Shows results or error
6. If CREATE TABLE, refreshes table list

**Flow:**
```
User types: "SELECT * FROM products"
User clicks "Execute SQL"
  → executeSql() called
  → POST /api/rdbms/sql { sql: "SELECT * FROM products" }
  → Backend executes SQL
  → Returns results
  → setSqlResult(response.data)
  → UI shows results
```

#### `loadTableData()` - Get Table Data

```javascript
const loadTableData = async (tableName) => {
  try {
    setLoading(true);
    const response = await axios.post(`${API_BASE_URL}/select`, {
      tableName: tableName,
      columns: null,      // SELECT *
      where: null,        // No WHERE clause
      limit: null,       // No LIMIT
      offset: null       // No OFFSET
    });
    setTableData(response.data);
  } catch (error) {
    alert('Error loading table data: ' + (error.response?.data?.error || error.message));
  } finally {
    setLoading(false);
  }
};
```

**What it does:**
1. User clicks a table in sidebar
2. Sends POST request to `/api/rdbms/select`
3. Backend returns all rows from that table
4. Updates `tableData` state
5. UI shows table data in a nice table format

---

### 5. UI Components

#### Sidebar - Table List

```javascript
<div className="sidebar">
  <h2>Tables</h2>
  <button className="btn-primary" onClick={loadTables}>
    Refresh
  </button>
  <div className="table-list">
    {tables.map(table => (
      <div
        key={table.id}
        className={`table-item ${selectedTable?.id === table.id ? 'active' : ''}`}
        onClick={() => handleTableClick(table)}
      >
        <strong>{table.tableName}</strong>
        <span className="table-count">{table.rows?.length || 0} rows</span>
      </div>
    ))}
  </div>
</div>
```

**What it does:**
- Shows list of all tables
- Each table is clickable
- Shows table name and row count
- Highlights selected table

#### SQL REPL Tab

```javascript
{activeTab === 'sql' && (
  <div className="sql-panel">
    <textarea
      value={sqlQuery}
      onChange={(e) => setSqlQuery(e.target.value)}
      placeholder="Enter SQL query here..."
      rows={15}
    />
    <button
      className="btn-execute"
      onClick={executeSql}
      disabled={loading || !sqlQuery.trim()}
    >
      {loading ? 'Executing...' : 'Execute SQL'}
    </button>
    
    {sqlError && (
      <div className="error-message">
        <strong>Error:</strong> {sqlError}
      </div>
    )}
    
    {sqlResult && (
      <div className="sql-result">
        <h3>Result:</h3>
        <pre>{JSON.stringify(sqlResult, null, 2)}</pre>
      </div>
    )}
  </div>
)}
```

**What it does:**
- Textarea for typing SQL
- Execute button
- Shows results (JSON formatted)
- Shows errors if SQL fails

#### Table Data Tab

```javascript
{activeTab === 'data' && (
  <div className="data-panel">
    {selectedTable ? (
      <>
        <h2>{selectedTable.tableName}</h2>
        <button onClick={() => loadTableData(selectedTable.tableName)}>
          Refresh Data
        </button>
        {loading ? (
          <div>Loading...</div>
        ) : (
          renderTableData()  // Shows data in table format
        )}
      </>
    ) : (
      <div>Select a table from the sidebar</div>
    )}
  </div>
)}
```

**What it does:**
- Shows selected table's data
- Displays data in HTML table format
- Refresh button to reload data

#### `renderTableData()` - Format Data as Table

```javascript
const renderTableData = () => {
  if (!tableData || tableData.length === 0) {
    return <div className="empty-state">No data available</div>;
  }

  const columns = Object.keys(tableData[0]);  // Get column names

  return (
    <div className="table-container">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map(col => (
              <th key={col}>{col}</th>  // Header row
            ))}
          </tr>
        </thead>
        <tbody>
          {tableData.map((row, idx) => (
            <tr key={idx}>
              {columns.map(col => (
                <td key={col}>{String(row[col] ?? 'NULL')}</td>  // Data rows
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
```

**What it does:**
- Takes array of row objects
- Extracts column names from first row
- Creates HTML table with headers
- Renders each row as table row
- Handles null values (shows 'NULL')

---

## How It All Works Together

### User Journey: Creating a Table

1. **User opens app** → `http://localhost:3000`
2. **App loads** → `useEffect` calls `loadTables()`
3. **Sidebar shows tables** → E-commerce tables appear
4. **User clicks "SQL REPL" tab**
5. **User types SQL:**
   ```sql
   CREATE TABLE customers (id INTEGER, name VARCHAR(100), PRIMARY KEY (id));
   ```
6. **User clicks "Execute SQL"**
7. **Frontend sends:**
   ```javascript
   POST http://localhost:8080/api/rdbms/sql
   { "sql": "CREATE TABLE customers ..." }
   ```
8. **Backend processes** → Creates table via RDBMS
9. **Backend returns** → Success response
10. **Frontend updates:**
    - Shows success result
    - Refreshes table list (new table appears in sidebar)

### User Journey: Viewing Table Data

1. **User clicks table in sidebar** → e.g., "products"
2. **Frontend calls:**
   ```javascript
   handleTableClick(table)
     → loadTableData("products")
     → POST /api/rdbms/select { tableName: "products" }
   ```
3. **Backend returns** → Array of row objects
4. **Frontend renders:**
   ```javascript
   renderTableData()
     → Creates HTML table
     → Shows all rows
   ```

---

## Key React Concepts Used

### 1. useState Hook
```javascript
const [tables, setTables] = useState([]);
```
- Creates state variable
- `tables` = current value
- `setTables` = function to update value
- When updated, React re-renders UI

### 2. useEffect Hook
```javascript
useEffect(() => {
  loadTables();
}, []);
```
- Runs when component mounts (first render)
- Empty array `[]` = run once
- Loads tables on app startup

### 3. Event Handlers
```javascript
onClick={() => handleTableClick(table)}
onChange={(e) => setSqlQuery(e.target.value)}
```
- Handle user interactions
- Update state
- Trigger re-renders

### 4. Conditional Rendering
```javascript
{activeTab === 'sql' && <div>SQL Panel</div>}
{sqlError && <div>Error message</div>}
{loading ? <div>Loading...</div> : <div>Content</div>}
```
- Show/hide UI based on state
- Ternary operator for if/else

### 5. Array Methods
```javascript
tables.map(table => <div>{table.tableName}</div>)
tableData.map(row => <tr>...</tr>)
```
- Transform arrays into UI elements
- Each item becomes a component

---

## API Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/rdbms/tables` | GET | Get all tables |
| `/api/rdbms/sql` | POST | Execute SQL query |
| `/api/rdbms/select` | POST | Select data from table |

---

## Styling

### `App.css` - Component Styles
- Modern, clean design
- Gradient header
- Tab navigation
- Responsive layout
- Hover effects
- Loading states

### `index.css` - Global Styles
- Resets default browser styles
- Sets base font family
- Background color

---

## Dependencies

### `package.json`
```json
{
  "react": "^18.2.0",        // React library
  "react-dom": "^18.2.0",    // React DOM rendering
  "react-scripts": "5.0.1",  // Build tools
  "axios": "^1.6.0"          // HTTP client
}
```

**What each does:**
- **react** - Core React library
- **react-dom** - Renders React to DOM
- **react-scripts** - Build/dev server (Create React App)
- **axios** - Makes HTTP requests to backend

---

## How to Run

```bash
cd frontend
npm install    # Install dependencies
npm start      # Start dev server (port 3000)
```

**What happens:**
1. Dev server starts on `http://localhost:3000`
2. Browser opens automatically
3. Hot reload enabled (changes refresh automatically)
4. Connects to backend on `http://localhost:8080`

---

## Summary

### What the Frontend Does

1. **Provides UI** - User-friendly interface
2. **SQL REPL** - Interactive SQL query interface
3. **Table Browser** - View tables and data
4. **API Communication** - Talks to backend via REST API
5. **State Management** - Manages UI state (tabs, data, loading)
6. **Error Handling** - Shows errors to user
7. **Loading States** - Shows spinners during requests

### Key Features

✅ **Two Tabs:**
- SQL REPL - Execute any SQL query
- Table Data - Browse table contents

✅ **Sidebar:**
- Lists all tables
- Shows row counts
- Click to view data

✅ **Real-time Updates:**
- Refresh tables after CREATE TABLE
- Update data on demand
- Show loading states

✅ **Error Handling:**
- Shows SQL errors clearly
- Handles network errors
- User-friendly messages

---

## For the Interview

**Question:** "How does the frontend work?"

**Answer:**
> "The frontend is a React application that provides a user interface for our RDBMS. It has two main features:
> 
> 1. **SQL REPL** - Users can type SQL queries and execute them. The frontend sends the SQL to `/api/rdbms/sql` endpoint, and displays the results.
> 
> 2. **Table Browser** - Users can see all tables in a sidebar, click on them to view their data. The frontend calls `/api/rdbms/select` to get the data and displays it in a table format.
> 
> The frontend uses React hooks (useState, useEffect) to manage state, axios for HTTP requests, and communicates with the backend via REST API. It's completely separate from the backend - runs on port 3000 while backend runs on 8080."

---

The frontend is a clean, simple React app that demonstrates our RDBMS through an interactive UI! 🎉
