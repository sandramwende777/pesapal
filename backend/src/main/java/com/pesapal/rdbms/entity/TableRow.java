package com.pesapal.rdbms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Metadata Entity: Represents a row of data in a user-created table.
 * 
 * This entity stores the actual data for user-created tables.
 * The row data is stored as a JSON string to support dynamic schemas.
 * 
 * ARCHITECTURE NOTE: Why JSON?
 * - User tables have different columns (we don't know schema at compile time)
 * - JSON allows flexible storage: {"id":1, "name":"Laptop", "price":999.99}
 * - We validate against TableColumn metadata when inserting/updating
 * 
 * Example: For "INSERT INTO products (id, name, price) VALUES (1, 'Laptop', 999.99)",
 *          we create a TableRow entity with:
 *          - table = DatabaseTable("products")
 *          - rowData = "{\"id\":1,\"name\":\"Laptop\",\"price\":999.99}"
 */
@Entity
@Table(name = "table_rows",
       indexes = {
           @Index(name = "idx_table_rows_table", columnList = "table_id"),
           @Index(name = "idx_table_rows_created", columnList = "created_at")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_table_row_table"))
    @JsonIgnore  // Prevent circular reference in JSON serialization
    private DatabaseTable table;

    @Column(name = "row_data", columnDefinition = "TEXT", nullable = false)
    private String rowData; // JSON string storing column_name -> value pairs

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
