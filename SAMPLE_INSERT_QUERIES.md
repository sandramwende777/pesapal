# Sample INSERT Queries for Orders and Order Items

## Orders Table Schema

**Columns:**
- `id` (INTEGER, PRIMARY KEY, NOT NULL)
- `customer_id` (INTEGER, NOT NULL)
- `order_date` (DATE, NOT NULL)
- `total_amount` (DECIMAL, NOT NULL)
- `status` (VARCHAR(50), NOT NULL, DEFAULT 'pending')

## Order Items Table Schema

**Columns:**
- `id` (INTEGER, PRIMARY KEY, NOT NULL)
- `order_id` (INTEGER, NOT NULL) - References orders.id
- `product_id` (INTEGER, NOT NULL) - References products.id
- `quantity` (INTEGER, NOT NULL)
- `price` (DECIMAL, NOT NULL)

---

## Sample INSERT Queries

### 1. Insert Orders

```sql
-- Order 1: Customer 1 orders on 2026-01-15
INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
VALUES (1, 1, '2026-01-15', 999.99, 'pending');

-- Order 2: Customer 2 orders on 2026-01-16
INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
VALUES (2, 2, '2026-01-16', 699.99, 'processing');

-- Order 3: Customer 1 orders again on 2026-01-17
INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
VALUES (3, 1, '2026-01-17', 69.98, 'completed');

-- Order 4: Customer 3 orders on 2026-01-17
INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
VALUES (4, 3, '2026-01-17', 19.99, 'pending');
```

**Note:** If you omit `status`, it will use the default value 'pending':
```sql
-- Using default status
INSERT INTO orders (id, customer_id, order_date, total_amount) 
VALUES (5, 2, '2026-01-17', 49.99);
```

### 2. Insert Order Items

```sql
-- Order Items for Order 1 (Customer 1's first order)
INSERT INTO order_items (id, order_id, product_id, quantity, price) 
VALUES (1, 1, 1, 1, 999.99);
-- This adds 1x Laptop to order 1

-- Order Items for Order 2 (Customer 2's order)
INSERT INTO order_items (id, order_id, product_id, quantity, price) 
VALUES (2, 2, 2, 1, 699.99);
-- This adds 1x Smartphone to order 2

-- Order Items for Order 3 (Customer 1's second order - multiple items)
INSERT INTO order_items (id, order_id, product_id, quantity, price) 
VALUES (3, 3, 3, 2, 19.99);
-- This adds 2x T-Shirt to order 3

INSERT INTO order_items (id, order_id, product_id, quantity, price) 
VALUES (4, 3, 4, 1, 49.99);
-- This adds 1x Programming Book to order 3 (same order)

-- Order Items for Order 4 (Customer 3's order)
INSERT INTO order_items (id, order_id, product_id, quantity, price) 
VALUES (5, 4, 3, 1, 19.99);
-- This adds 1x T-Shirt to order 4
```

### 3. Complete Example: Create an Order with Multiple Items

```sql
-- Step 1: Create the order
INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
VALUES (10, 1, '2026-01-17', 1049.98, 'pending');

-- Step 2: Add items to the order
-- Item 1: Laptop
INSERT INTO order_items (id, order_id, product_id, quantity, price) 
VALUES (10, 10, 1, 1, 999.99);

-- Item 2: T-Shirt
INSERT INTO order_items (id, order_id, product_id, quantity, price) 
VALUES (11, 10, 3, 1, 19.99);

-- Item 3: Programming Book
INSERT INTO order_items (id, order_id, product_id, quantity, price) 
VALUES (12, 10, 4, 1, 49.99);
```

---

## Using the API

### Via SQL Endpoint (Recommended)

```bash
# Insert an order
curl -X POST http://localhost:8080/api/rdbms/sql \
  -H "Content-Type: application/json" \
  -d '{"sql": "INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (1, 1, '\''2026-01-15'\'', 999.99, '\''pending'\'')"}'

# Insert an order item
curl -X POST http://localhost:8080/api/rdbms/sql \
  -H "Content-Type: application/json" \
  -d '{"sql": "INSERT INTO order_items (id, order_id, product_id, quantity, price) VALUES (1, 1, 1, 1, 999.99)"}'
```

### Via Direct API Endpoints

```bash
# Insert order
curl -X POST http://localhost:8080/api/rdbms/insert \
  -H "Content-Type: application/json" \
  -d '{
    "tableName": "orders",
    "values": {
      "id": 1,
      "customer_id": 1,
      "order_date": "2026-01-15",
      "total_amount": 999.99,
      "status": "pending"
    }
  }'

# Insert order item
curl -X POST http://localhost:8080/api/rdbms/insert \
  -H "Content-Type: application/json" \
  -d '{
    "tableName": "order_items",
    "values": {
      "id": 1,
      "order_id": 1,
      "product_id": 1,
      "quantity": 1,
      "price": 999.99
    }
  }'
```

---

## Query Examples

### View all orders
```sql
SELECT * FROM orders;
```

### View all order items
```sql
SELECT * FROM order_items;
```

### View orders with their items (JOIN)
```sql
SELECT 
    o.id AS order_id,
    o.customer_id,
    o.order_date,
    o.total_amount,
    o.status,
    oi.id AS item_id,
    oi.product_id,
    oi.quantity,
    oi.price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id;
```

### View order details with product names
```sql
SELECT 
    o.id AS order_id,
    o.customer_id,
    o.order_date,
    o.total_amount,
    o.status,
    p.name AS product_name,
    oi.quantity,
    oi.price AS item_price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
JOIN products p ON oi.product_id = p.id;
```

---

## Important Notes

1. **Primary Keys**: The `id` fields are primary keys, so each must be unique
2. **Foreign Keys**: 
   - `order_items.order_id` should reference an existing `orders.id`
   - `order_items.product_id` should reference an existing `products.id`
3. **Date Format**: Use `'YYYY-MM-DD'` format for dates (e.g., `'2026-01-15'`)
4. **Status Values**: Common values: `'pending'`, `'processing'`, `'completed'`, `'cancelled'`
5. **Total Amount**: Should match the sum of order items (though not enforced by constraints)
