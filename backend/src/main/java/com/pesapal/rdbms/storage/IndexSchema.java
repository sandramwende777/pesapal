package com.pesapal.rdbms.storage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an index definition.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexSchema {
    
    private String indexName;
    private String columnName;
    private boolean unique;
    private IndexType indexType = IndexType.BTREE;  // For future use
    
    public IndexSchema(String indexName, String columnName, boolean unique) {
        this.indexName = indexName;
        this.columnName = columnName;
        this.unique = unique;
        this.indexType = IndexType.BTREE;
    }
}
