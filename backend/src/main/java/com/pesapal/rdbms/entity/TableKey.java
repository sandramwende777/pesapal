package com.pesapal.rdbms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata Entity: Represents a key constraint (PRIMARY KEY or UNIQUE KEY) 
 * on a column in a user-created table.
 * 
 * This entity stores metadata about constraints for tables created dynamically.
 * Used to validate data integrity when inserting/updating rows.
 * 
 * Example: For "CREATE TABLE products (id INTEGER PRIMARY KEY)",
 *          we create a TableKey entity with:
 *          - columnName="id"
 *          - keyType=PRIMARY
 */
@Entity
@Table(name = "table_keys",
       uniqueConstraints = {
           @UniqueConstraint(
               name = "uk_table_column_keytype",
               columnNames = {"table_id", "column_name", "key_type"}
           )
       },
       indexes = {
           @Index(name = "idx_table_keys_table", columnList = "table_id"),
           @Index(name = "idx_table_keys_column", columnList = "column_name")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_table_key_table"))
    @JsonIgnore  // Prevent circular reference in JSON serialization
    private DatabaseTable table;

    @Column(name = "column_name", nullable = false, length = 255)
    private String columnName;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 20)
    private KeyType keyType;

    public enum KeyType {
        PRIMARY, UNIQUE, FOREIGN
    }
}
