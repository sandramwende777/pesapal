package com.pesapal.rdbms.storage.index;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Represents the execution details of a query.
 * Used for EXPLAIN-like functionality and performance monitoring.
 */
@Data
@Builder
public class QueryExecution {
    
    private String tableName;
    private String queryType;  // SELECT, UPDATE, DELETE
    
    // Index usage
    private boolean indexUsed;
    private String indexName;
    private String indexColumn;
    private String indexOperation;  // LOOKUP, RANGE_SCAN, etc.
    
    // Performance metrics
    private int rowsScanned;
    private int rowsReturned;
    private long executionTimeMs;
    
    // Query details
    private Map<String, Object> whereClause;
    private List<String> selectedColumns;
    
    /**
     * Returns a human-readable execution plan.
     */
    public String getExecutionPlan() {
        StringBuilder sb = new StringBuilder();
        sb.append("Query Execution Plan:\n");
        sb.append("=====================\n");
        sb.append("Table: ").append(tableName).append("\n");
        sb.append("Type: ").append(queryType).append("\n");
        
        if (indexUsed) {
            sb.append("Access Method: INDEX LOOKUP\n");
            sb.append("  Index: ").append(indexName).append("\n");
            sb.append("  Column: ").append(indexColumn).append("\n");
            sb.append("  Operation: ").append(indexOperation).append("\n");
        } else {
            sb.append("Access Method: FULL TABLE SCAN\n");
        }
        
        sb.append("Rows Scanned: ").append(rowsScanned).append("\n");
        sb.append("Rows Returned: ").append(rowsReturned).append("\n");
        sb.append("Execution Time: ").append(executionTimeMs).append(" ms\n");
        
        return sb.toString();
    }
    
    /**
     * Returns a compact log message.
     */
    public String toLogMessage() {
        if (indexUsed) {
            return String.format("[%s] %s: Using index '%s' on %s.%s (%s) -> %d rows in %d ms",
                    queryType, tableName, indexName, tableName, indexColumn, 
                    indexOperation, rowsReturned, executionTimeMs);
        } else {
            return String.format("[%s] %s: Full table scan -> scanned %d rows, returned %d in %d ms",
                    queryType, tableName, rowsScanned, rowsReturned, executionTimeMs);
        }
    }
}
