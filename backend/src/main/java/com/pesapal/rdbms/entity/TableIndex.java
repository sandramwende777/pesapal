package com.pesapal.rdbms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata Entity: Represents an index definition on a column in a user-created table.
 * 
 * This entity stores metadata about indexes for tables created dynamically.
 * Used for query optimization and performance.
 * 
 * Example: For "CREATE INDEX idx_category ON products (category_id)",
 *          we create a TableIndex entity with:
 *          - indexName="idx_category"
 *          - columnName="category_id"
 */
@Entity
@Table(name = "table_indexes",
       uniqueConstraints = {
           @UniqueConstraint(
               name = "uk_table_index_name",
               columnNames = {"table_id", "index_name"}
           )
       },
       indexes = {
           @Index(name = "idx_table_indexes_table", columnList = "table_id"),
           @Index(name = "idx_table_indexes_column", columnList = "column_name")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_table_index_table"))
    @JsonIgnore  // Prevent circular reference in JSON serialization
    private DatabaseTable table;

    @Column(name = "index_name", nullable = false, length = 255)
    private String indexName;

    @Column(name = "column_name", nullable = false, length = 255)
    private String columnName;

    @Column(name = "is_unique", nullable = false)
    private Boolean unique = false;
}
