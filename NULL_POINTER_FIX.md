# NullPointerException Fix

## Problem
The backend was failing during initialization with:
```
java.lang.NullPointerException: Cannot invoke "java.util.List.add(Object)" because the return value of "com.pesapal.rdbms.dto.CreateTableRequest.getIndexes()" is null
        at com.pesapal.rdbms.config.DataInitializer.initializeEcommerceTables(DataInitializer.java:94)
```

## Root Cause
When creating the "orders" and "order_items" tables, the code was trying to add indexes to a list that was never initialized:
- `ordersRequest.setIndexes()` was never called
- `orderItemsRequest.setIndexes()` was never called
- When the code tried to call `getIndexes().add()`, it failed because `getIndexes()` returned `null`

## Solution
Added initialization of the `indexes` and `uniqueKeys` lists for both tables:

**For "orders" table:**
```java
ordersRequest.setIndexes(new ArrayList<>());
ordersRequest.setUniqueKeys(new ArrayList<>());
```

**For "order_items" table:**
```java
orderItemsRequest.setIndexes(new ArrayList<>());
orderItemsRequest.setUniqueKeys(new ArrayList<>());
```

## Result
✅ All four e-commerce tables are now created successfully:
- `products`
- `categories`
- `orders`
- `order_items`

✅ Backend starts without errors
✅ Sample data is inserted correctly

## Verification
```bash
# Check all tables
curl http://localhost:8080/api/rdbms/tables

# Should show: products, categories, orders, order_items
```
