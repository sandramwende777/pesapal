# Quick Start Guide - Run the Application

## ✅ Prerequisites Installed

- ✅ Java installed
- ✅ npm installed  
- ✅ Frontend dependencies installed (`npm install` completed)

## 🚀 Start the Application

### Terminal 1: Backend (Spring Boot)

```bash
cd /Users/sandramwende/Interviews/pesapal/backend
./gradlew bootRun
```

**Wait for this message:**
```
Started RdbmsApplication in X.XXX seconds
```

**Backend will be at:** `http://localhost:8080`

### Terminal 2: Frontend (React)

**Open a NEW terminal** and run:

```bash
cd /Users/sandramwende/Interviews/pesapal/frontend
npm start
```

**Browser will open automatically at:** `http://localhost:3000`

---

## 🎯 What You'll See

### In the Browser:

1. **Header**: "Simple RDBMS Demo - E-Commerce"
2. **Sidebar (Left)**: 
   - List of tables: products, categories, orders, order_items
   - Row counts for each table
   - "Refresh" button
3. **Main Area (Right)**:
   - Two tabs: "SQL REPL" and "Table Data"

---

## 🧪 Try These Things

### 1. View Table Data
- Click "products" in the sidebar
- Click "Table Data" tab
- See all product data in a table

### 2. Execute SQL Query
- Click "SQL REPL" tab
- Type: `SELECT * FROM products;`
- Click "Execute SQL"
- See results!

### 3. Create a New Table
In SQL REPL, type:
```sql
CREATE TABLE customers (
    id INTEGER,
    name VARCHAR(100),
    email VARCHAR(100),
    PRIMARY KEY (id)
);
```
Click "Execute SQL" → Table appears in sidebar!

### 4. Insert Data
```sql
INSERT INTO customers (id, name, email) 
VALUES (1, 'John Doe', 'john@example.com');
```

### 5. Join Tables
```sql
SELECT * FROM products p 
INNER JOIN categories c 
ON p.category_id = c.id;
```

---

## 🔍 Verify Backend is Running

### Test API:
```bash
curl http://localhost:8080/api/rdbms/tables
```

Should return JSON with tables.

### H2 Console (Optional):
1. Open: `http://localhost:8080/h2-console`
2. JDBC URL: `jdbc:h2:mem:rdbmsdb`
3. Username: `sa`
4. Password: (leave empty)
5. Click "Connect"
6. See all tables and data!

---

## ⚠️ Troubleshooting

### Backend not starting?
- Check backend terminal for errors
- Make sure port 8080 is not in use
- Wait 30-60 seconds (Spring Boot takes time)

### Frontend not starting?
- Make sure backend is running first
- Check port 3000 is not in use
- Try: `cd frontend && npm install` again

### CORS errors?
- Backend must be running on port 8080
- Frontend must be running on port 3000
- Check browser console for errors

### No tables showing?
- Check backend logs for initialization errors
- Tables are created on startup by `DataInitializer`
- Try refreshing the sidebar

---

## 📊 Sample Data

The app automatically creates:
- **products**: 4 sample products (Laptop, Smartphone, T-Shirt, Programming Book)
- **categories**: 3 categories (Electronics, Clothing, Books)
- **orders**: Empty (you can create your own)
- **order_items**: Empty (you can create your own)

---

## 🎉 You're Ready!

Once both are running:
1. ✅ Backend: `http://localhost:8080` (API)
2. ✅ Frontend: `http://localhost:3000` (UI)

**Start exploring the RDBMS!**
