package com.pesapal.rdbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata Entity: Represents a user-created table in our RDBMS.
 * 
 * ARCHITECTURE NOTE: Why we use metadata entities instead of in-memory structures:
 * 
 * 1. We're BUILDING a simple RDBMS, not just using one. When users create tables
 *    dynamically (e.g., "CREATE TABLE products"), we need to store the schema.
 * 
 * 2. This entity stores METADATA about user-created tables:
 *    - Table name (e.g., "products", "orders")
 *    - Column definitions (via TableColumn entities)
 *    - Key constraints (via TableKey entities)
 *    - Index definitions (via TableIndex entities)
 *    - Row data (via TableRow entities)
 * 
 * 3. Benefits of storing metadata in database tables:
 *    - Persistence: Schema survives application restarts
 *    - Queryable: Can execute "SHOW TABLES" or "DESCRIBE table"
 *    - Validation: Can validate constraints when inserting data
 *    - Demonstrates understanding of how databases store their own metadata
 * 
 * 4. This is NOT an e-commerce entity - it's the ENGINE that powers our RDBMS.
 *    It stores information ABOUT tables, not the actual business data.
 * 
 * Example: When user executes "CREATE TABLE products (id INTEGER, name VARCHAR(100))",
 *          we create a DatabaseTable entity with tableName="products" and related
 *          TableColumn entities for "id" and "name".
 */
@Entity
@Table(name = "database_tables",
       indexes = {
           @Index(name = "idx_database_tables_name", columnList = "table_name"),
           @Index(name = "idx_database_tables_created", columnList = "created_at")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name", unique = true, nullable = false, length = 255)
    private String tableName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TableColumn> columns = new ArrayList<>();

    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TableIndex> indexes = new ArrayList<>();

    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TableKey> keys = new ArrayList<>();

    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TableRow> rows = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
