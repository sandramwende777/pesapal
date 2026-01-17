import React, { useState, useEffect } from 'react';
import './App.css';
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api/rdbms';

function App() {
  const [activeTab, setActiveTab] = useState('sql');
  const [tables, setTables] = useState([]);
  const [selectedTable, setSelectedTable] = useState(null);
  const [tableData, setTableData] = useState([]);
  const [sqlQuery, setSqlQuery] = useState('');
  const [sqlResult, setSqlResult] = useState(null);
  const [sqlError, setSqlError] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadTables();
  }, []);

  const loadTables = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/tables`);
      setTables(response.data);
    } catch (error) {
      console.error('Error loading tables:', error);
    }
  };

  const loadTableData = async (tableName) => {
    try {
      setLoading(true);
      const response = await axios.post(`${API_BASE_URL}/select`, {
        tableName: tableName,
        columns: null,
        where: null,
        limit: null,
        offset: null
      });
      setTableData(response.data);
    } catch (error) {
      console.error('Error loading table data:', error);
      alert('Error loading table data: ' + (error.response?.data?.error || error.message));
    } finally {
      setLoading(false);
    }
  };

  const executeSql = async () => {
    try {
      setLoading(true);
      setSqlError(null);
      const response = await axios.post(`${API_BASE_URL}/sql`, {
        sql: sqlQuery
      });
      setSqlResult(response.data);
      
      // Refresh tables if a table was created
      if (sqlQuery.toUpperCase().trim().startsWith('CREATE TABLE')) {
        loadTables();
      }
    } catch (error) {
      setSqlError(error.response?.data?.error || error.message);
      setSqlResult(null);
    } finally {
      setLoading(false);
    }
  };

  const handleTableClick = (table) => {
    setSelectedTable(table);
    loadTableData(table.tableName);
  };

  const renderTableData = () => {
    if (!tableData || tableData.length === 0) {
      return <div className="empty-state">No data available</div>;
    }

    const columns = Object.keys(tableData[0]);

    return (
      <div className="table-container">
        <table className="data-table">
          <thead>
            <tr>
              {columns.map(col => (
                <th key={col}>{col}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {tableData.map((row, idx) => (
              <tr key={idx}>
                {columns.map(col => (
                  <td key={col}>{String(row[col] ?? 'NULL')}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Simple RDBMS Demo</h1>
        <p>Create tables, insert data, and query using SQL or REST API</p>
      </header>

      <div className="app-container">
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
                <span className="table-count">{table.rowCount ?? table.rows?.length ?? 0} rows</span>
              </div>
            ))}
          </div>
        </div>

        <div className="main-content">
          <div className="tabs">
            <button
              className={activeTab === 'sql' ? 'tab active' : 'tab'}
              onClick={() => setActiveTab('sql')}
            >
              SQL REPL
            </button>
            <button
              className={activeTab === 'data' ? 'tab active' : 'tab'}
              onClick={() => setActiveTab('data')}
            >
              Table Data
            </button>
          </div>

          {activeTab === 'sql' && (
            <div className="sql-panel">
              <div className="sql-editor">
                <textarea
                  value={sqlQuery}
                  onChange={(e) => setSqlQuery(e.target.value)}
                  placeholder="Enter SQL query here...&#10;&#10;Examples:&#10;CREATE TABLE products (id INTEGER, name VARCHAR(100), price DECIMAL(10,2), PRIMARY KEY (id));&#10;INSERT INTO products (id, name, price) VALUES (1, 'Laptop', 999.99);&#10;SELECT * FROM products;&#10;UPDATE products SET price = 899.99 WHERE id = 1;&#10;DELETE FROM products WHERE id = 1;"
                  rows={15}
                />
              </div>
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

          {activeTab === 'data' && (
            <div className="data-panel">
              {selectedTable ? (
                <>
                  <div className="table-info">
                    <h2>{selectedTable.tableName}</h2>
                    <button
                      className="btn-secondary"
                      onClick={() => loadTableData(selectedTable.tableName)}
                    >
                      Refresh Data
                    </button>
                  </div>
                  {loading ? (
                    <div className="loading">Loading...</div>
                  ) : (
                    renderTableData()
                  )}
                </>
              ) : (
                <div className="empty-state">
                  Select a table from the sidebar to view its data
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default App;
