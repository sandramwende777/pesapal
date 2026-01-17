# E-Commerce Entities Added! 🎉

## What I Created

I've added **traditional e-commerce JPA entities** alongside the existing RDBMS metadata entities. Now you have **BOTH** approaches!

## Two Types of Entities in the Project

### 1. RDBMS Metadata Entities (Original)
**Location:** `backend/src/main/java/com/pesapal/rdbms/entity/`

These are for the RDBMS engine itself:
- `DatabaseTable` - Stores info about user-created tables
- `TableColumn` - Stores column definitions
- `TableRow` - Stores row data (as JSON)
- `TableKey` - Stores key constraints
- `TableIndex` - Stores index definitions

**Purpose:** These power the RDBMS system itself.

### 2. E-Commerce Entities (NEW! ✨)
**Location:** `backend/src/main/java/com/pesapal/rdbms/ecommerce/entity/`

These are traditional JPA entities for e-commerce:
- `Product` - Product entity with name, price, stock, category
- `Category` - Category entity with name, description
- `Order` - Order entity with customer info, total, status
- `OrderItem` - Order line items with product, quantity, price

**Purpose:** These demonstrate traditional Spring Boot/JPA usage.

---

## New File Structure

```
backend/src/main/java/com/pesapal/rdbms/
├── entity/                    ← RDBMS Metadata Entities
│   ├── DatabaseTable.java
│   ├── TableColumn.java
│   └── ...
│
└── ecommerce/                 ← NEW! E-Commerce Entities
    ├── entity/
    │   ├── Product.java       ← Traditional JPA entity
    │   ├── Category.java      ← Traditional JPA entity
    │   ├── Order.java          ← Traditional JPA entity
    │   └── OrderItem.java      ← Traditional JPA entity
    │
    ├── repository/
    │   ├── ProductRepository.java
    │   ├── CategoryRepository.java
    │   ├── OrderRepository.java
    │   └── OrderItemRepository.java
    │
    ├── service/
    │   └── EcommerceService.java
    │
    ├── controller/
    │   └── EcommerceController.java
    │
    └── config/
        └── EcommerceDataInitializer.java
```

---

## What Each E-Commerce Entity Does

### Product Entity
```java
@Entity
@Table(name = "ecommerce_products")
public class Product {
    @Id
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    
    @ManyToOne
    private Category category;  // Relationship to Category
}
```

**Features:**
- ✅ Traditional JPA entity (not stored as JSON)
- ✅ Relationship to Category
- ✅ Indexes on category_id and name
- ✅ Automatic timestamps (created_at, updated_at)

### Category Entity
```java
@Entity
@Table(name = "ecommerce_categories")
public class Category {
    @Id
    private Long id;
    
    @Column(unique = true)
    private String name;  // Unique constraint!
    
    private String description;
    
    @OneToMany(mappedBy = "category")
    private List<Product> products;  // Relationship to Products
}
```

**Features:**
- ✅ Unique constraint on name
- ✅ One-to-many relationship with Products
- ✅ Automatic timestamp

### Order Entity
```java
@Entity
@Table(name = "ecommerce_orders")
public class Order {
    @Id
    private Long id;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private LocalDateTime orderDate;
    private BigDecimal totalAmount;
    private String status;
    
    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;  // Relationship to OrderItems
}
```

**Features:**
- ✅ Indexes on customer_id, order_date, status
- ✅ One-to-many relationship with OrderItems
- ✅ Automatic timestamps

### OrderItem Entity
```java
@Entity
@Table(name = "ecommerce_order_items")
public class OrderItem {
    @Id
    private Long id;
    
    @ManyToOne
    private Order order;  // Relationship to Order
    
    @ManyToOne
    private Product product;  // Relationship to Product
    
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal subtotal;  // Auto-calculated!
}
```

**Features:**
- ✅ Many-to-one relationships (Order and Product)
- ✅ Auto-calculates subtotal (quantity × price)
- ✅ Indexes on order_id and product_id

---

## New API Endpoints

### Categories
- `POST /api/ecommerce/categories` - Create category
- `GET /api/ecommerce/categories` - Get all categories
- `GET /api/ecommerce/categories/{id}` - Get category by ID

### Products
- `POST /api/ecommerce/products` - Create product
- `GET /api/ecommerce/products` - Get all products
- `GET /api/ecommerce/products/{id}` - Get product by ID
- `GET /api/ecommerce/products/category/{categoryId}` - Get products by category
- `PUT /api/ecommerce/products/{id}/stock` - Update stock

### Orders
- `POST /api/ecommerce/orders` - Create order
- `GET /api/ecommerce/orders` - Get all orders
- `GET /api/ecommerce/orders/{id}` - Get order by ID
- `GET /api/ecommerce/orders/customer/{customerId}` - Get orders by customer
- `PUT /api/ecommerce/orders/{id}/status` - Update order status

---

## Example Usage

### Create a Category
```bash
POST http://localhost:8080/api/ecommerce/categories
{
  "name": "Electronics",
  "description": "Electronic devices"
}
```

### Create a Product
```bash
POST http://localhost:8080/api/ecommerce/products
{
  "name": "Laptop",
  "description": "High-performance laptop",
  "price": 999.99,
  "stock": 50,
  "categoryId": 1
}
```

### Create an Order
```bash
POST http://localhost:8080/api/ecommerce/orders
{
  "customerId": 1,
  "customerName": "John Doe",
  "customerEmail": "john@example.com",
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ]
}
```

---

## Key Differences: Metadata vs E-Commerce Entities

| Aspect | Metadata Entities | E-Commerce Entities |
|--------|------------------|---------------------|
| **Purpose** | Power the RDBMS | Store e-commerce data |
| **Storage** | Store metadata about tables | Store actual product/order data |
| **Structure** | Generic (table, column, row) | Specific (product, order, etc.) |
| **Data Format** | JSON strings in TableRow | Native JPA columns |
| **Relationships** | Foreign keys in metadata | JPA @ManyToOne/@OneToMany |
| **Use Case** | Dynamic table creation | Traditional CRUD operations |

---

## Why Both?

This demonstrates **two different approaches**:

1. **RDBMS Metadata Entities** - Shows how to build a database management system
2. **E-Commerce Entities** - Shows traditional Spring Boot/JPA usage

**For the interview, you can explain:**
- "The metadata entities power our custom RDBMS"
- "The e-commerce entities demonstrate traditional JPA usage"
- "Both coexist and serve different purposes"

---

## Database Tables Created

When you start the app, Spring Boot will create:

### RDBMS Metadata Tables (Original)
- `database_tables`
- `table_columns`
- `table_keys`
- `table_indexes`
- `table_rows`

### E-Commerce Tables (NEW!)
- `ecommerce_products`
- `ecommerce_categories`
- `ecommerce_orders`
- `ecommerce_order_items`

**Both sets of tables exist in the same H2 database!**

---

## Sample Data

The `EcommerceDataInitializer` automatically creates:
- 3 categories (Electronics, Clothing, Books)
- 4 products (Laptop, Smartphone, T-Shirt, Programming Book)

When you start the app, this data is automatically created!

---

## Testing the New Entities

### 1. Start the Backend
```bash
cd backend
./gradlew bootRun
```

### 2. Test the API
```bash
# Get all products
curl http://localhost:8080/api/ecommerce/products

# Get all categories
curl http://localhost:8080/api/ecommerce/categories

# Create a new product
curl -X POST http://localhost:8080/api/ecommerce/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Gaming Mouse",
    "description": "High-precision gaming mouse",
    "price": 79.99,
    "stock": 25,
    "categoryId": 1
  }'
```

### 3. View in H2 Console
1. Go to: `http://localhost:8080/h2-console`
2. Login with: `jdbc:h2:mem:rdbmsdb`, `sa`, (empty password)
3. You'll see both:
   - RDBMS metadata tables
   - E-commerce tables (`ecommerce_products`, `ecommerce_categories`, etc.)

---

## Summary

✅ **Added traditional e-commerce JPA entities**
✅ **Product, Category, Order, OrderItem entities**
✅ **Repositories, Service, Controller for e-commerce**
✅ **Sample data initialization**
✅ **REST API endpoints**

Now you have:
- **RDBMS entities** (for the database system)
- **E-commerce entities** (for business data)

Both work together in the same application! 🎉
