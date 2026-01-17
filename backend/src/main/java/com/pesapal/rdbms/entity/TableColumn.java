package com.pesapal.rdbms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata Entity: Represents a column definition in a user-created table.
 * 
 * This entity stores metadata about columns (name, data type, nullable, etc.)
 * for tables created dynamically by users. It's part of the RDBMS metadata system.
 * 
 * Example: For "CREATE TABLE products (id INTEGER, name VARCHAR(100))",
 *          we create two TableColumn entities:
 *          - One for "id" with dataType=INTEGER
 *          - One for "name" with dataType=VARCHAR, maxLength=100
 */
@Entity
@Table(name = "table_columns",
       uniqueConstraints = {
           @UniqueConstraint(
               name = "uk_table_column_name",
               columnNames = {"table_id", "column_name"}
           )
       },
       indexes = {
           @Index(name = "idx_table_columns_table", columnList = "table_id")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_table_column_table"))
    @JsonIgnore  // Prevent circular reference in JSON serialization
    private DatabaseTable table;

    @Column(name = "column_name", nullable = false, length = 255)
    private String columnName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private DataType dataType;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "is_nullable", nullable = false)
    private Boolean nullable = true;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    public enum DataType {
        VARCHAR, INTEGER, BIGINT, DECIMAL, BOOLEAN, DATE, TIMESTAMP, TEXT
    }
}
