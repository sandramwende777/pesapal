package com.pesapal.rdbms.storage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a key constraint (PRIMARY KEY, UNIQUE, FOREIGN KEY).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeySchema {
    
    private String columnName;
    private KeyType keyType;
    
    // For foreign keys
    private String referencesTable;
    private String referencesColumn;
}
